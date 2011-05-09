package org.fcrepo.server.security;

import java.net.URI;
import java.net.URISyntaxException;
import java.text.DateFormat;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Set;

import org.jrdf.graph.PredicateNode;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import org.fcrepo.common.Constants;
import org.fcrepo.common.rdf.SimpleURIReference;

import org.fcrepo.server.ReadOnlyContext;
import org.fcrepo.server.Server;
import org.fcrepo.server.errors.ServerException;
import org.fcrepo.server.storage.DOManager;
import org.fcrepo.server.storage.DOReader;
import org.fcrepo.server.storage.types.RelationshipTuple;

import com.sun.xacml.EvaluationCtx;

public class DateEmbargoAttributeFinderModule
extends AttributeFinderModule {

	private static final Logger logger =
		LoggerFactory.getLogger(DateEmbargoAttributeFinderModule.class);
	
	public static final String ATTRIBUTE_URI = Constants.RESOURCE.uri + ":embargoed";
    public static final String ATTRIBUTE_TYPE = null; // String
	
    private final EmbargoDatePropertyType m_propType;
    private final String m_propName;
    private final String m_dateFormat;
    private final PredicateNode m_propNode;
	public DateEmbargoAttributeFinderModule(
			EmbargoDatePropertyType propType,
			String propName,
			String dateFormat){
		super();
		m_propType = propType;
		m_propName = propName;
		m_dateFormat = dateFormat;
		PredicateNode propNode = null;
		try {
			if (m_propType == EmbargoDatePropertyType.RELSEXT) {
				propNode = new SimpleURIReference(new URI(m_propName));
			}
			else {
				propNode = null;
			}
			registerAttribute(ATTRIBUTE_URI,ATTRIBUTE_TYPE);
            setInstantiatedOk(true);
		}
		catch (URISyntaxException e){
            setInstantiatedOk(false);
		}
		finally {
			m_propNode = propNode;
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
				logger.debug("couldn't get object reader");
				return null;
			}
			String[] values = null;
            switch (m_propType){
            case OBJECT:
            	break;
            case RELSEXT:
            	break;
            default:
            	logger.warn("Unknown property type for date embargo: " + m_propType);
            	return null;
            }

			return values;
		} finally {
			long dur = System.currentTimeMillis() - getAttributeStartTime;
			logger.debug("Locally getting the '" + attributeId
					+ "' attribute for this resource took " + dur + "ms.");
		}
	}

	private DOManager doManager = null;

	public void setDOManager(DOManager doManager) {
		if (this.doManager == null) {
			this.doManager = doManager;
		}
	}
	
	private long getDateFromRels(DOReader reader) throws ServerException {
		long result = -1;
		Set<RelationshipTuple> rels = reader.getRelationships(m_propNode, null);
		if (!rels.isEmpty()){
			DateFormat df = new SimpleDateFormat(m_dateFormat);
			for (RelationshipTuple rel:rels){
				try {
					long d = df.parse(rel.object).getTime();
					if (d > result) result = d;
				} catch (ParseException e) {
					logger.warn("Could not parse date property: " + rel.object);
				}
			}
		}
		return result;
	}

}

