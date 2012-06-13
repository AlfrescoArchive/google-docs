
package org.alfresco.integrations.google.docs.webscripts;

import java.util.HashMap;
import java.util.Map;

import org.alfresco.integrations.google.docs.service.GoogleDocsService;
import org.alfresco.service.cmr.repository.NodeRef;
import org.springframework.extensions.webscripts.Cache;
import org.springframework.extensions.webscripts.DeclarativeWebScript;
import org.springframework.extensions.webscripts.Status;
import org.springframework.extensions.webscripts.WebScriptRequest;

public class HasConcurrentEditors extends DeclarativeWebScript
{
    private GoogleDocsService googledocsService;

    private final static String MODEL_CONCURRENT_EDITORS = "concurrentEditors";

    private final static String PARAM_NODEREF = "nodeRef";

    public void setGoogledocsService(GoogleDocsService googledocsService)
    {
        this.googledocsService = googledocsService;
    }

    @Override
    protected Map<String, Object> executeImpl(WebScriptRequest req, Status status, Cache cache)
    {
        Map<String, Object> model = new HashMap<String, Object>();

        String param_nodeRef = req.getParameter(PARAM_NODEREF);
        NodeRef nodeRef = new NodeRef(param_nodeRef);

        model.put(MODEL_CONCURRENT_EDITORS, googledocsService.hasConcurrentEditors(nodeRef));

        return model;
    }

}
