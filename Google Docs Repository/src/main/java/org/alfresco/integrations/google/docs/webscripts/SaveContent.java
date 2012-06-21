
package org.alfresco.integrations.google.docs.webscripts;


import java.io.IOException;
import java.io.Serializable;
import java.util.HashMap;
import java.util.Map;

import org.alfresco.integrations.google.docs.GoogleDocsConstants;
import org.alfresco.integrations.google.docs.service.GoogleDocsService;
import org.alfresco.model.ContentModel;
import org.alfresco.repo.version.Version2Model;
import org.alfresco.service.cmr.repository.NodeRef;
import org.alfresco.service.cmr.repository.NodeService;
import org.alfresco.service.cmr.version.VersionService;
import org.alfresco.service.cmr.version.VersionType;
import org.apache.commons.httpclient.HttpStatus;
import org.json.JSONException;
import org.json.JSONObject;
import org.springframework.extensions.surf.util.Content;
import org.springframework.extensions.webscripts.Cache;
import org.springframework.extensions.webscripts.DeclarativeWebScript;
import org.springframework.extensions.webscripts.Status;
import org.springframework.extensions.webscripts.WebScriptException;
import org.springframework.extensions.webscripts.WebScriptRequest;


/**
 * @author Jared Ottley <jared.ottley@alfresco.com>
 */
public class SaveContent
    extends DeclarativeWebScript
{
    private GoogleDocsService   googledocsService;
    private NodeService         nodeService;
    private VersionService      versionService;

    private static final String JSON_KEY_NODEREF      = "nodeRef";
    private static final String JSON_KEY_MAJORVERSION = "majorVersion";
    private static final String JSON_KEY_DESCRIPTION  = "description";

    private static final String MODEL_SUCCESS         = "success";


    public void setGoogledocsService(GoogleDocsService googledocsService)
    {
        this.googledocsService = googledocsService;
    }


    public void setNodeService(NodeService nodeService)
    {
        this.nodeService = nodeService;
    }


    public void setVersionService(VersionService versionService)
    {
        this.versionService = versionService;
    }


    @Override
    protected Map<String, Object> executeImpl(WebScriptRequest req, Status status, Cache cache)
    {
        Map<String, Object> model = new HashMap<String, Object>();

        boolean success = false;

        Map<String, Serializable> map = parseContent(req);
        NodeRef nodeRef = (NodeRef)map.get(JSON_KEY_NODEREF);

        String contentType = googledocsService.getContentType(nodeRef);

        if (contentType.equals(GoogleDocsConstants.DOCUMENT_TYPE))
        {
            googledocsService.getDocument(nodeRef);
            success = true; // TODO Make getDocument return boolean
        }
        else if (contentType.equals(GoogleDocsConstants.SPREADSHEET_TYPE))
        {
            googledocsService.getSpreadSheet(nodeRef);
            success = true; // TODO Make getSpreadsheet return boolean
        }
        else if (contentType.equals(GoogleDocsConstants.PRESENTATION_TYPE))
        {
            googledocsService.getPresentation(nodeRef);
            success = true; // TODO Make getPresentation return boolean
        }
        else
        {
            throw new WebScriptException(500, "Content Type: " + contentType + " unknown.");
        }

        // Finish this off with a version create or update
        Map<String, Serializable> versionProperties = new HashMap<String, Serializable>();
        if (nodeService.hasAspect(nodeRef, ContentModel.ASPECT_VERSIONABLE))
        {
            versionProperties.put(Version2Model.PROP_VERSION_TYPE, map.get(JSON_KEY_MAJORVERSION));
            versionProperties.put(Version2Model.PROP_DESCRIPTION, map.get(JSON_KEY_DESCRIPTION));
        }
        else
        {
            versionProperties.put(Version2Model.PROP_VERSION_TYPE, VersionType.MAJOR);

            nodeService.setProperty(nodeRef, ContentModel.PROP_AUTO_VERSION, true);
            nodeService.setProperty(nodeRef, ContentModel.PROP_AUTO_VERSION_PROPS, true);
        }

        versionService.createVersion(nodeRef, versionProperties);

        model.put(MODEL_SUCCESS, success);

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

                if (nodeService.hasAspect(nodeRef, ContentModel.ASPECT_VERSIONABLE))
                {
                    result.put(JSON_KEY_MAJORVERSION, json.getBoolean(JSON_KEY_MAJORVERSION) ? VersionType.MAJOR
                                                                                            : VersionType.MINOR);
                    result.put(JSON_KEY_DESCRIPTION, json.getString(JSON_KEY_DESCRIPTION));
                }
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
