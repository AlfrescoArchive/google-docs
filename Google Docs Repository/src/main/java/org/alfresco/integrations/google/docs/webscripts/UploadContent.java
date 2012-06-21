
package org.alfresco.integrations.google.docs.webscripts;


import java.util.HashMap;
import java.util.Map;

import org.alfresco.integrations.google.docs.service.GoogleDocsService;
import org.alfresco.service.cmr.repository.NodeRef;
import org.springframework.extensions.webscripts.Cache;
import org.springframework.extensions.webscripts.DeclarativeWebScript;
import org.springframework.extensions.webscripts.Status;
import org.springframework.extensions.webscripts.WebScriptRequest;

import com.google.gdata.data.docs.DocumentListEntry;


/**
 * @author Jared Ottley <jared.ottley@alfresco.com>
 */
public class UploadContent
    extends DeclarativeWebScript
{

    private GoogleDocsService   googledocsService;

    private static final String PARAM_NODEREF = "nodeRef";
    private static final String MODEL_NODEREF = "nodeRef";


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

        DocumentListEntry entry = googledocsService.uploadFile(nodeRef);

        googledocsService.decorateNode(nodeRef, entry, false);

        model.put(MODEL_NODEREF, nodeRef.toString());

        return model;
    }

}
