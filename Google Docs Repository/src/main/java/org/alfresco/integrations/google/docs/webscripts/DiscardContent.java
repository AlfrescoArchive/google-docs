
package org.alfresco.integrations.google.docs.webscripts;


import java.io.IOException;
import java.io.Serializable;
import java.util.HashMap;
import java.util.Map;

import org.alfresco.integrations.google.docs.GoogleDocsModel;
import org.alfresco.integrations.google.docs.exceptions.GoogleDocsAuthenticationException;
import org.alfresco.integrations.google.docs.exceptions.GoogleDocsRefreshTokenException;
import org.alfresco.integrations.google.docs.exceptions.GoogleDocsServiceException;
import org.alfresco.integrations.google.docs.service.GoogleDocsService;
import org.alfresco.model.ContentModel;
import org.alfresco.service.cmr.repository.InvalidNodeRefException;
import org.alfresco.service.cmr.repository.NodeRef;
import org.alfresco.service.cmr.repository.NodeService;
import org.apache.commons.httpclient.HttpStatus;
import org.json.JSONException;
import org.json.JSONObject;
import org.springframework.extensions.surf.util.Content;
import org.springframework.extensions.webscripts.Cache;
import org.springframework.extensions.webscripts.DeclarativeWebScript;
import org.springframework.extensions.webscripts.Status;
import org.springframework.extensions.webscripts.WebScriptException;
import org.springframework.extensions.webscripts.WebScriptRequest;

import com.google.gdata.data.docs.DocumentListEntry;


public class DiscardContent
    extends DeclarativeWebScript
{
    private GoogleDocsService   googledocsService;
    private NodeService         nodeService;

    private static final String JSON_KEY_NODEREF = "nodeRef";

    private static final String MODEL_SUCCESS    = "success";


    public void setGoogledocsService(GoogleDocsService googledocsService)
    {
        this.googledocsService = googledocsService;
    }


    public void setNodeService(NodeService nodeService)
    {
        this.nodeService = nodeService;
    }


    @Override
    protected Map<String, Object> executeImpl(WebScriptRequest req, Status status, Cache cache)
    {
        Map<String, Object> model = new HashMap<String, Object>();

        Map<String, Serializable> nodeRefs = parseContent(req);
        NodeRef nodeRef = (NodeRef)nodeRefs.get(JSON_KEY_NODEREF);

        if (nodeService.hasAspect(nodeRef, GoogleDocsModel.ASPECT_GOOGLEDOCS))
        {
            DocumentListEntry documentListEntry;
            try
            {
                boolean deleted = false;
                
                documentListEntry = googledocsService.getDocumentListEntry(nodeService.getProperty(nodeRef, GoogleDocsModel.PROP_RESOURCE_ID).toString());
                googledocsService.unlockNode(nodeRef);
                deleted = googledocsService.deleteContent(nodeRef, documentListEntry);

                if (deleted)
                {
                    if (nodeService.hasAspect(nodeRef, ContentModel.ASPECT_TEMPORARY))
                    {
                        nodeService.deleteNode(nodeRef);
                    }
                }
                else
                {
                    throw new WebScriptException(HttpStatus.SC_METHOD_FAILURE, "Unable to Delete Document from Google Docs.");
                }
                
                model.put(MODEL_SUCCESS, deleted);

            }
            catch (InvalidNodeRefException ine)
            {
                throw new WebScriptException(HttpStatus.SC_NOT_FOUND, ine.getMessage());
            }
            catch (IOException ioe)
            {
                throw new WebScriptException(HttpStatus.SC_INTERNAL_SERVER_ERROR, ioe.getMessage());
            }
            catch (GoogleDocsAuthenticationException gdae)
            {
                throw new WebScriptException(HttpStatus.SC_UNAUTHORIZED, gdae.getMessage());
            }
            catch (GoogleDocsRefreshTokenException gdrte)
            {
                throw new WebScriptException(HttpStatus.SC_BAD_GATEWAY, gdrte.getMessage());
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


        }
        else
        {
            throw new WebScriptException(HttpStatus.SC_NOT_ACCEPTABLE, "Missing Google Docs Aspect on " + nodeRef.toString());
        }

        return model;
    }


    private Map<String, Serializable> parseContent(final WebScriptRequest req)
    {
        final Map<String, Serializable> result = new HashMap<String, Serializable>();
        Content content = req.getContent();
        String jsonStr = null;
        JSONObject json = null;

        try
        {
            if (content == null || content.getSize() == 0)
            {
                throw new WebScriptException(HttpStatus.SC_BAD_REQUEST, "No content sent with request.");
            }

            jsonStr = content.getContent();

            if (jsonStr == null || jsonStr.trim().length() == 0)
            {
                throw new WebScriptException(HttpStatus.SC_BAD_REQUEST, "No content sent with request.");
            }

            json = new JSONObject(jsonStr);

            if (!json.has(JSON_KEY_NODEREF))
            {
                throw new WebScriptException(HttpStatus.SC_BAD_REQUEST, "Key " + JSON_KEY_NODEREF + " is missing from JSON: "
                                                                        + jsonStr);
            }
            else
            {
                NodeRef nodeRef = new NodeRef(json.getString(JSON_KEY_NODEREF));
                result.put(JSON_KEY_NODEREF, nodeRef);
            }
        }
        catch (final IOException ioe)
        {
            throw new WebScriptException(HttpStatus.SC_INTERNAL_SERVER_ERROR, ioe.getMessage(), ioe);
        }
        catch (final JSONException je)
        {
            throw new WebScriptException(HttpStatus.SC_BAD_REQUEST, "Unable to parse JSON: " + jsonStr);
        }
        catch (final WebScriptException wse)
        {
            throw wse; // Ensure WebScriptExceptions get rethrown verbatim
        }
        catch (final Exception e)
        {
            throw new WebScriptException(HttpStatus.SC_BAD_REQUEST, "Unable to parse JSON '" + jsonStr + "'.", e);
        }

        return result;
    }

}
