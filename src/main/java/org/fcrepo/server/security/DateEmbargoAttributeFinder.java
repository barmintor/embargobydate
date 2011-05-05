package org.fcrepo.server.security;

import java.net.URI;
        
public class DateEmbargoAttributeFinder
        extends AttributeFinderModule {

    @Override
    protected boolean canHandleAdhoc() {
        // TODO Auto-generated method stub
                return false;
    }

    @Override
    protected Object getAttributeLocally(int designatorType,
                                         String attributeId,
                                         URI resourceCategory,
                                         EvaluationCtx context) {
        // TODO Auto-generated method stub
                return null;
    }

}

    