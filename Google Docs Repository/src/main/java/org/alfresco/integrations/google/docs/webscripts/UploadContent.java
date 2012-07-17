
package org.alfresco.integrations.google.docs.webscripts;


import java.io.IOException;
import java.util.HashMap;
import java.util.Map;

import org.alfresco.integrations.google.docs.exceptions.GoogleDocsAuthenticationException;
import org.alfresco.integrations.google.docs.exceptions.GoogleDocsRefreshTokenException;
import org.alfresco.integrations.google.docs.exceptions.GoogleDocsServiceException;
import org.alfresco.integrations.google.docs.service.GoogleDocsService;
import org.alfresco.service.cmr.repository.NodeRef;
import org.apache.commons.httpclient.HttpStatus;
import org.springframework.extensions.webscripts.Cache;
import org.springframework.extensions.webscripts.DeclarativeWebScript;
import org.springframework.extensions.webscripts.Status;
import org.springframework.extensions.webscripts.WebScriptException;
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

        DocumentListEntry entry;
        try
        {
            entry = googledocsService.uploadFile(nodeRef);
            googledocsService.decorateNode(nodeRef, entry, false);
            googledocsService.lockNode(nodeRef);
        }
        catch (GoogleDocsAuthenticationException gdae)
        {
            throw new WebScriptException(HttpStatus.SC_BAD_GATEWAY, gdae.getMessage());
        }
        catch (GoogleDocsServiceException gdse)
        {
            if (gdse.getPassedStatusCode() > -1)
            {
                throw new WebScriptException(gdse.getPassedStatusCode(), gdse.getMessage());
            }
            else
            {
                throw new WebScriptException(gdse.getMessage());
            }
        }
        catch (GoogleDocsRefreshTokenException gdrte)
        {
            throw new WebScriptException(HttpStatus.SC_BAD_GATEWAY, gdrte.getMessage());
        }
        catch (IOException ioe)
        {
            throw new WebScriptException(HttpStatus.SC_INTERNAL_SERVER_ERROR, ioe.getMessage(), ioe);
        }

        model.put(MODEL_NODEREF, nodeRef.toString());

        return model;
    }

}
