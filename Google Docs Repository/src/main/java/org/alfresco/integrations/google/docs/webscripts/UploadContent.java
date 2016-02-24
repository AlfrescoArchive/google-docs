/**
 * Copyright (C) 2005-2015 Alfresco Software Limited.
 * 
 * This file is part of Alfresco
 * 
 * Alfresco is free software: you can redistribute it and/or modify it under the terms of the GNU Lesser General Public License as
 * published by the Free Software Foundation, either version 3 of the License, or (at your option) any later version.
 * 
 * Alfresco is distributed in the hope that it will be useful, but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the GNU Lesser General Public License for more details.
 * 
 * You should have received a copy of the GNU Lesser General Public License along with Alfresco. If not, see
 * <http://www.gnu.org/licenses/>.
 */

package org.alfresco.integrations.google.docs.webscripts;

import java.io.IOException;
import java.io.Serializable;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import com.google.api.client.auth.oauth2.Credential;
import com.google.api.services.drive.model.File;
import org.alfresco.integrations.google.docs.GoogleDocsModel;
import org.alfresco.integrations.google.docs.exceptions.GoogleDocsAuthenticationException;
import org.alfresco.integrations.google.docs.exceptions.GoogleDocsRefreshTokenException;
import org.alfresco.integrations.google.docs.exceptions.GoogleDocsServiceException;
import org.alfresco.integrations.google.docs.service.GoogleDocsService;
import org.alfresco.integrations.google.docs.service.GoogleDocsService.GooglePermission;
import org.alfresco.model.ContentModel;
import org.alfresco.repo.version.Version2Model;
import org.alfresco.service.cmr.repository.NodeRef;
import org.alfresco.service.cmr.repository.NodeService;
import org.alfresco.service.cmr.version.VersionService;
import org.alfresco.service.cmr.version.VersionType;
import org.apache.commons.httpclient.HttpStatus;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import org.springframework.extensions.surf.util.Content;
import org.springframework.extensions.webscripts.Cache;
import org.springframework.extensions.webscripts.Status;
import org.springframework.extensions.webscripts.WebScriptException;
import org.springframework.extensions.webscripts.WebScriptRequest;


/**
 * @author Jared Ottley <jared.ottley@alfresco.com>
 */
public class UploadContent
    extends GoogleDocsWebScripts
{
    private static final Log    log           = LogFactory.getLog(UploadContent.class);

    private GoogleDocsService   googledocsService;
    private VersionService      versionService;

    private static final String PARAM_NODEREF                    = "nodeRef";
    private static final String PARAM_PERMISSIONS                = "permissions";
    private static final String PARAM_SEND_EMAIL                 = "sendEmail";
    private static final String MODEL_NODEREF                    = "nodeRef";
    private static final String MODEL_EDITOR_URL                 = "editorUrl";

    private static final String JSON_KEY_PERMISSIONS             = "permissions";
    private static final String JSON_KEY_PERMISSIONS_ITEMS       = "items";
    private static final String JSON_KEY_PERMISSIONS_SEND_EMAIL  = "sendEmail";
    private static final String JSON_KEY_AUTHORITY_ID            = "authorityId";
    private static final String JSON_KEY_AUTHORITY_TYPE          = "authorityType";
    private static final String JSON_VAL_AUTHORITY_TYPE_DEFAULT  = "user";
    private static final String JSON_KEY_ROLE_NAME               = "roleName";


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


    @SuppressWarnings("unchecked")
    @Override
    protected Map<String, Object> executeImpl(WebScriptRequest req, Status status, Cache cache)
    {
        getGoogleDocsServiceSubsystem();

        Map<String, Object> model = new HashMap<>();

        if (googledocsService.isEnabled())
        {
        
        String param_nodeRef = req.getParameter(PARAM_NODEREF);
        NodeRef nodeRef = new NodeRef(param_nodeRef);

        Map<String, Serializable> jsonParams = parseContent(req);

        File file;
        try
        {
            Credential credential = googledocsService.getCredential();

            if (nodeService.hasAspect(nodeRef, GoogleDocsModel.ASPECT_EDITING_IN_GOOGLE))
            {
                // Check the doc exists in Google - it may have been removed accidentally
                try
                {
                    file = googledocsService.getDriveFile(credential, nodeRef);
                }
                catch(GoogleDocsServiceException gdse)
                {
                    file = googledocsService.uploadFile(credential, nodeRef);
                    if (log.isDebugEnabled())
                    {
                        log.debug(nodeRef + " Uploaded to Google.");
                    }
                    // Re-apply the previous permissions, if they exist
                    if (nodeService.getProperty(nodeRef, GoogleDocsModel.PROP_CURRENT_PERMISSIONS) != null)
                    {
                        googledocsService.addRemotePermissions(credential,
                                file,
                                googledocsService.getGooglePermissions(nodeRef, GoogleDocsModel.PROP_CURRENT_PERMISSIONS)
                        );
                    }
                }
            }
            else
            {
                file = googledocsService.uploadFile(credential, nodeRef);
                if (log.isDebugEnabled())
                {
                    log.debug(nodeRef + " Uploaded to Google.");
                }
            }

            if (jsonParams.containsKey(PARAM_PERMISSIONS))
            {
                if (log.isDebugEnabled())
                {
                    log.debug("Adding permissions to remote object");
                }
                googledocsService.addRemotePermissions(credential,
                        file,
                        (List<GooglePermission>) jsonParams.get(PARAM_PERMISSIONS)
                );
            }

            // If this is a non-cloud instance of Alfresco, we need to make the
            // node versionable before we start working on it. We want the the
            // version component to be triggered on save. The versionable aspect
            // is only added if this is existing content, not if it was just
            // created where the document is the initial version when saved
            if (!nodeService.hasAspect(nodeRef, ContentModel.ASPECT_TEMPORARY)
                && !nodeService.hasAspect(nodeRef, ContentModel.ASPECT_VERSIONABLE))
            {
                Map<String, Serializable> versionProperties = new HashMap<>();
                versionProperties.put(Version2Model.PROP_VERSION_TYPE, VersionType.MAJOR);

                nodeService.setProperty(nodeRef, ContentModel.PROP_AUTO_VERSION, true);
                // autoVersionOnUpdateProps now set to false to follow Share upload scripts (fixes GOOGLEDOCS-111)
                nodeService.setProperty(nodeRef, ContentModel.PROP_AUTO_VERSION_PROPS, false);

                log.debug("Version Node:" + nodeRef + "; Version Properties: " + versionProperties);
                versionService.createVersion(nodeRef, versionProperties);
            }

            if (googledocsService.isLockedByGoogleDocs(nodeRef))
            {
                googledocsService.unlockNode(nodeRef);
            }

            // The alternateLink returned by an uploaded file directs the user to a preview. Need to get the alternateLink
            // provided by a straight file get if we want to direct a user to an editor UI. Make the request with the known
            // ID as it as not yet been set on the node yet
            //file = googledocsService.getDriveFile(credential, file.getId());

            googledocsService.decorateNode(nodeRef, file, googledocsService.getLatestRevision(credential, file), (List<GooglePermission>) jsonParams.get(PARAM_PERMISSIONS), false);
            googledocsService.lockNode(nodeRef);
        }
        catch (GoogleDocsAuthenticationException gdae)
        {
            throw new WebScriptException(HttpStatus.SC_BAD_GATEWAY, gdae.getMessage(), gdae);
        }
        catch (GoogleDocsServiceException gdse)
        {
            if (gdse.getPassedStatusCode() > -1)
            {
                throw new WebScriptException(gdse.getPassedStatusCode(), gdse.getMessage(), gdse);
            }
            else
            {
                throw new WebScriptException(gdse.getMessage());
            }
        }
        catch (GoogleDocsRefreshTokenException gdrte)
        {
            throw new WebScriptException(HttpStatus.SC_BAD_GATEWAY, gdrte.getMessage(), gdrte);
        }
        catch (IOException ioe)
        {
            throw new WebScriptException(HttpStatus.SC_INTERNAL_SERVER_ERROR, ioe.getMessage(), ioe);
        }
        catch (Exception e)
        {
            throw new WebScriptException(HttpStatus.SC_INTERNAL_SERVER_ERROR, e.getMessage(), e);
        }

        model.put(MODEL_NODEREF, nodeRef.toString());
        model.put(MODEL_EDITOR_URL, file.getAlternateLink());
        
        }
        else
        {
            throw new WebScriptException(HttpStatus.SC_SERVICE_UNAVAILABLE, "Google Docs Disabled");
        }

        return model;
    }

    private Map<String, Serializable> parseContent(final WebScriptRequest req)
    {
        final Map<String, Serializable> result = new HashMap<>();
        Content content = req.getContent();
        String jsonStr = null;
        JSONObject json = null;

        try
        {
            if (content == null || content.getSize() == 0)
            {
                return result;
            }

            jsonStr = content.getContent();

            if (jsonStr == null || jsonStr.trim().length() == 0)
            {
                throw new WebScriptException(HttpStatus.SC_BAD_REQUEST, "No content sent with request.");
            }
            log.debug("Parsed JSON: " + jsonStr);

            json = new JSONObject(jsonStr);

            if (json.has(JSON_KEY_PERMISSIONS))
            {
                JSONObject permissionData = json.getJSONObject(JSON_KEY_PERMISSIONS);
                boolean sendEmail = permissionData.has(JSON_KEY_PERMISSIONS_SEND_EMAIL) ? 
                        permissionData.getBoolean(JSON_KEY_PERMISSIONS_SEND_EMAIL) : true;
                if (!permissionData.has(JSON_KEY_PERMISSIONS_ITEMS))
                {
                    throw new WebScriptException(HttpStatus.SC_BAD_REQUEST, "Key " + JSON_KEY_PERMISSIONS_ITEMS + " is missing from JSON object: "
                            + permissionData.toString());
                }
                JSONArray jsonPerms = permissionData.getJSONArray(JSON_KEY_PERMISSIONS_ITEMS);
                ArrayList<GooglePermission> permissions = new ArrayList<>(jsonPerms.length());
                for (int i = 0; i < jsonPerms.length(); i++)
                {
                    JSONObject jsonPerm = jsonPerms.getJSONObject(i);
                    String authorityId, authorityType, roleName;
                    if (jsonPerm.has(JSON_KEY_AUTHORITY_ID))
                    {
                        authorityId = jsonPerm.getString(JSON_KEY_AUTHORITY_ID);
                    }
                    else
                    {
                        throw new WebScriptException(HttpStatus.SC_BAD_REQUEST, "Key " + JSON_KEY_AUTHORITY_ID + " is missing from JSON object: "
                                                                                + jsonPerm.toString());
                    }
                    if (jsonPerm.has(JSON_KEY_AUTHORITY_TYPE))
                    {
                        authorityType = jsonPerm.getString(JSON_KEY_AUTHORITY_TYPE);
                    }
                    else
                    {
                        authorityType = JSON_VAL_AUTHORITY_TYPE_DEFAULT;
                    }
                    if (jsonPerm.has(JSON_KEY_ROLE_NAME))
                    {
                        roleName = jsonPerm.getString(JSON_KEY_ROLE_NAME);
                    }
                    else
                    {
                        throw new WebScriptException(HttpStatus.SC_BAD_REQUEST, "Key " + JSON_KEY_ROLE_NAME + " is missing from JSON object: "
                                                                                + jsonPerm.toString());
                    }
                    permissions.add(new GooglePermission(authorityId, authorityType, roleName));
                }
                result.put(PARAM_PERMISSIONS, permissions);
                result.put(PARAM_SEND_EMAIL, Boolean.valueOf(sendEmail));
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
