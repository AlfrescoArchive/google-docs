package org.alfresco.integrations.google.docs.webscripts;

import java.util.HashMap;
import java.util.Map;

import org.alfresco.integrations.google.docs.GoogleDocsConstants;
import org.alfresco.integrations.google.docs.service.GoogleDocsService;
import org.alfresco.service.cmr.repository.NodeRef;
import org.springframework.extensions.webscripts.Cache;
import org.springframework.extensions.webscripts.DeclarativeWebScript;
import org.springframework.extensions.webscripts.Status;
import org.springframework.extensions.webscripts.WebScriptException;
import org.springframework.extensions.webscripts.WebScriptRequest;

public class SaveContent extends DeclarativeWebScript
{
    private GoogleDocsService googledocsService;
    
    private static final String PARAM_NODEREF = "nodeRef";
    private static final String MODEL_SUCCESS = "success";
    
    public void setGoogledocsService(GoogleDocsService googledocsService)
    {
        this.googledocsService = googledocsService;
    }

    @Override
    protected Map<String, Object> executeImpl(WebScriptRequest req, Status status, Cache cache)
    {
        Map<String, Object> model = new HashMap<String, Object>();
        
        boolean success = false;
        String param_nodeRef = req.getParameter(PARAM_NODEREF);
        NodeRef nodeRef = new NodeRef(param_nodeRef);
        
        String contentType = googledocsService.getContentType(nodeRef);
        
        if (contentType.equals(GoogleDocsConstants.DOCUMENT_TYPE))
        {
            googledocsService.getDocument(nodeRef);
            success = true; //TODO Make getDocument return boolean
        }
        else if (contentType.equals(GoogleDocsConstants.SPREADSHEET_TYPE))
        {
            googledocsService.getSpreadSheet(nodeRef);
            success = true; //TODO Make getSpreadsheet return boolean
        }
        else if (contentType.equals(GoogleDocsConstants.PRESENTATION_TYPE))
        {
            googledocsService.getPresentation(nodeRef);
            success = true; //TODO Make getPresentation return boolean
        }
        else
        {
            throw new WebScriptException(500, "Content Type: " + contentType + " unknown.");
        }
        
        model.put(MODEL_SUCCESS, success);
        
        return model;
    }

}
