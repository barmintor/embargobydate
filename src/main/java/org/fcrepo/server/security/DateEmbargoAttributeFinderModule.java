package org.fcrepo.server.security;

import java.net.URI;
import java.net.URISyntaxException;
import java.text.DateFormat;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.Set;

import org.jrdf.graph.PredicateNode;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import org.fcrepo.common.Constants;
import org.fcrepo.common.rdf.SimpleURIReference;

import org.fcrepo.server.security.AttributeFinderModule;
import org.fcrepo.server.ReadOnlyContext;
import org.fcrepo.server.Server;
import org.fcrepo.server.errors.InitializationException;
import org.fcrepo.server.errors.ServerException;
import org.fcrepo.server.resourceIndex.ResourceIndex;
import org.fcrepo.server.storage.DOManager;
import org.fcrepo.server.storage.DOReader;
import org.fcrepo.server.storage.types.RelationshipTuple;

import com.sun.xacml.EvaluationCtx;
import com.sun.xacml.attr.AttributeDesignator;
import com.sun.xacml.attr.StringAttribute;
import com.sun.xacml.cond.EvaluationResult;

public class DateEmbargoAttributeFinderModule
extends AttributeFinderModule {

    private static final Logger logger =
        LoggerFactory.getLogger(DateEmbargoAttributeFinderModule.class);

    public static final String ATTRIBUTE_URI = Constants.RESOURCE.uri + ":embargoed";
    public static final String ATTRIBUTE_TYPE = "http://www.w3.org/2001/XMLSchema#string"; // String

    private EmbargoDatePropertyType m_propType;
    private final String m_propName;
    private final String m_dateFormat;
    private final PredicateNode m_pnode;
    public DateEmbargoAttributeFinderModule(
                                             String propName,
                                             EmbargoDatePropertyType type,
                                             String dateFormat) {
        m_propType = type;
        m_propName = propName;
        m_dateFormat = dateFormat;
        PredicateNode propNode = null;
        try {
            if (m_propType == EmbargoDatePropertyType.OBJECT_RELS) {
                propNode = new SimpleURIReference(new URI(m_propName));
            }
            else {
                propNode = null;
            }
            registerSupportedDesignatorType(AttributeDesignator.RESOURCE_TARGET);
            registerAttribute(ATTRIBUTE_URI,ATTRIBUTE_TYPE);
            setInstantiatedOk(true);
        }
        catch (URISyntaxException e){
            setInstantiatedOk(false);
        }
        finally {
            m_pnode = propNode;
        }
    }

    public void init() throws InitializationException {
        switch (m_propType){
            case OBJECT_PROPS:
                break;
            case OBJECT_RELS:
                if (m_pnode == null) throw new InitializationException("relationship predicate property not set");
                break;
            default:
                break;
        }
    }

    @Override
    protected boolean canHandleAdhoc() {
        return false;
    }

    @Override
    protected Object getAttributeLocally(int designatorType,
                                         String attributeId,
                                         URI resourceCategory,
                                         EvaluationCtx context) {
        long getAttributeStartTime = System.currentTimeMillis();

        String[] values = null;
        try {
            String pid = PolicyFinderModule.getPid(context);
            if ("".equals(pid)) {
                logger.debug("no pid");
                return null;
            }
            logger.debug("getResourceAttribute, pid=" + pid);
            DOReader reader = null;
            try {
                logger.debug("pid=" + pid);
                reader =
                    doManager.getReader(Server.USE_DEFINITIVE_STORE,
                                        ReadOnlyContext.EMPTY,
                                        pid);
            } catch (ServerException e) {
                logger.error("couldn't get object reader",e);
                return null;
            }

            if (ATTRIBUTE_URI.equals(attributeId)){
                switch (m_propType){
                    case OBJECT_PROPS:
                        try{
                            long end = getLatestDateFromObject(reader);
                            values = new String[]{Boolean.toString((end > getAttributeStartTime))};
                        } catch (ServerException e) {
                            logger.error("couldn't get object relationships",e);
                        }
                        break;
                    case OBJECT_RELS:
                        String objuri = null;
                        String dsId = getDatastreamId(context);
                        try {
                            objuri = "info:fedora/" + reader.GetObjectPID();
                            String dsuri = (dsId == null)? null : objuri + "/" + dsId;
                            long end = getLatestDateFromRels(reader, objuri, dsuri);
                            values = new String[]{Boolean.toString((end > getAttributeStartTime))};
                        } catch (ServerException e) {
                            logger.error("couldn't get object relationships",e);
                        }
                        break;
                    default:
                        logger.warn("Unknown property type for date embargo: " + m_propType);
                }
            }
            else {
                logger.warn("Unexpected attribute id: " + attributeId);
            }
            return values;
        } finally {
            if (logger.isDebugEnabled()){
                long dur = System.currentTimeMillis() - getAttributeStartTime;
                if (values == null) {
                    logger.debug("Found no values for " + ATTRIBUTE_URI);
                }
                else {
                    for (String value: values){
                        logger.debug("Found value \"" +  value + "\" for " + ATTRIBUTE_URI);
                    }
                }
                logger.debug("Locally getting the '" + attributeId
                             + "' attribute for this resource took " + dur + "ms.");
            }
        }
    }

    private DOManager doManager = null;

    public void setDOManager(DOManager doManager) {
        if (this.doManager == null) {
            this.doManager = doManager;
        }
    }

    private long getLatestDateFromRels(DOReader reader, String objuri, String dsuri) throws ServerException {
        long objresult = -1;
        long dsresult = -1;
        Set<RelationshipTuple> rels = reader.getRelationships(null, m_pnode, null);
        if (!rels.isEmpty()){
            DateFormat df = new SimpleDateFormat(m_dateFormat);
            for (RelationshipTuple rel:rels){
                try {
                    if (rel.predicate.equals(this.m_propName)){
                        if (rel.subject.equals(objuri)){
                            long d = df.parse(rel.object).getTime();
                            if (d > objresult) objresult = d;
                        }
                        else if (rel.subject.equals(dsuri)){
                            long d = df.parse(rel.object).getTime();
                            if (d > dsresult) dsresult = d;
                        }
                    }
                    else {
                        logger.warn("unexpected predicate: " + rel.predicate + " expected: " + m_propName);
                    }
                } catch (ParseException e) {
                    logger.warn("Could not parse date property: " + rel.object);
                }
            }
        }
        return (dsresult == -1)? objresult:dsresult;
    }

    private long getLatestDateFromObject(DOReader reader) throws ServerException {
        long result = -1;
        String prop = reader.getObject().getExtProperty(m_propName);
        if (prop != null){
            DateFormat df = new SimpleDateFormat(m_dateFormat);
            try {
                result = df.parse(prop).getTime();
            } catch (ParseException e) {
                logger.warn("Could not parse date property: " + prop);
            }
        }
        return result;
    }	
    private final String getDatastreamId(EvaluationCtx context) {
        URI datastreamIdUri = null;
        try {
            datastreamIdUri = new URI(Constants.DATASTREAM.ID.uri);
        } catch (URISyntaxException e) {
        }

        EvaluationResult attribute =
            context.getResourceAttribute(STRING_ATTRIBUTE_URI,
                                         datastreamIdUri,
                                         null);

        Object element = getAttributeFromEvaluationResult(attribute);
        if (element == null) {
            logger.debug("getDatastreamId: " + " exit on "
                         + "can't get resource-id on request callback");
            return null;
        }

        if (!(element instanceof StringAttribute)) {
            logger.debug("getDatastreamId: " + " exit on "
                         + "couldn't get resource-id from xacml request "
                         + "non-string returned");
            return null;
        }

        String datastreamId = ((StringAttribute) element).getValue();

        if (datastreamId == null) {
            logger.debug("getDatastreamId: " + " exit on " + "null resource-id");
            return null;
        }

        if (!validDatastreamId(datastreamId)) {
            logger.debug("invalid resource-id: datastreamId is not valid");
            return null;
        }

        return datastreamId;
    }

    private final boolean validDatastreamId(String datastreamId) {
        if (datastreamId == null) {
            return false;
        }
        // "" is a valid resource id, for it represents a don't-care condition
        if (" ".equals(datastreamId)) {
            return false;
        }
        return true;
    }


}

