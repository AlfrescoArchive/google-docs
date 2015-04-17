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
import java.util.HashMap;
import java.util.Map;

import com.google.api.client.auth.oauth2.Credential;
import org.alfresco.integrations.google.docs.GoogleDocsConstants;
import org.alfresco.integrations.google.docs.exceptions.ConcurrentEditorException;
import org.alfresco.integrations.google.docs.exceptions.GoogleDocsAuthenticationException;
import org.alfresco.integrations.google.docs.exceptions.GoogleDocsRefreshTokenException;
import org.alfresco.integrations.google.docs.exceptions.GoogleDocsServiceException;
import org.alfresco.integrations.google.docs.service.GoogleDocsService;
import org.alfresco.model.ContentModel;
import org.alfresco.repo.security.authentication.AuthenticationUtil;
import org.alfresco.repo.security.permissions.AccessDeniedException;
import org.alfresco.repo.transaction.AlfrescoTransactionSupport;
import org.alfresco.repo.transaction.TransactionListenerAdapter;
import org.alfresco.repo.transaction.RetryingTransactionHelper.RetryingTransactionCallback;
import org.alfresco.repo.version.Version2Model;
import org.alfresco.service.cmr.dictionary.ConstraintException;
import org.alfresco.service.cmr.repository.NodeRef;
import org.alfresco.service.cmr.repository.NodeService;
import org.alfresco.service.cmr.site.SiteInfo;
import org.alfresco.service.cmr.site.SiteService;
import org.alfresco.service.cmr.version.Version;
import org.alfresco.service.cmr.version.VersionService;
import org.alfresco.service.cmr.version.VersionType;
import org.alfresco.service.transaction.TransactionService;
import org.apache.commons.httpclient.HttpStatus;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
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
public class SaveContent
    extends GoogleDocsWebScripts
{
    private static final Log    log                      = LogFactory.getLog(SaveContent.class);

    private GoogleDocsService   googledocsService;
    private VersionService      versionService;
    private TransactionService  transactionService;
    private SiteService         siteService;

    private static final String JSON_KEY_NODEREF         = "nodeRef";
    private static final String JSON_KEY_MAJORVERSION    = "majorVersion";
    private static final String JSON_KEY_DESCRIPTION     = "description";
    private static final String JSON_KEY_OVERRIDE        = "override";
    private static final String JSON_KEY_REMOVEFROMDRIVE = "removeFromDrive";

    private static final String MODEL_SUCCESS            = "success";
    private static final String MODEL_VERSION            = "version";


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


    public void setTransactionService(TransactionService transactionService)
    {
        this.transactionService = transactionService;
    }


    public void setSiteService(SiteService siteService)
    {
        this.siteService = siteService;
    }


    @Override
    protected Map<String, Object> executeImpl(WebScriptRequest req, Status status, Cache cache)
    {
        getGoogleDocsServiceSubsystem();

        Map<String, Object> model = new HashMap<String, Object>();

        boolean success = false;

        Map<String, Serializable> map = parseContent(req);
        final NodeRef nodeRef = (NodeRef)map.get(JSON_KEY_NODEREF);
        log.debug("Saving Node to Alfresco from Google: " + nodeRef);

        try
        {
            Credential credential = googledocsService.getCredential();

            SiteInfo siteInfo = null;
            String pathElement = getPathElement(nodeRef, 2);

            //Is the node in a site?
            if (pathElement.equals(GoogleDocsConstants.ALF_SITES_PATH_FQNS_ELEMENT))
            {
                try
                {
                    siteInfo = siteService.getSite(nodeRef);
                }
                catch (org.alfresco.repo.security.permissions.AccessDeniedException e)
                {
                    // When the user does not have permission to access the site node
                    // We can't get the name of the site that the node is located in
                    // So we can't place it in a site specific folder.
                    // It will be placed in the root of the Working Directory
                    log.debug("User does not have access to the containing sites info.  The document will be retrieved from the root of the working directory. {" + nodeRef.toString() + "}");
                }
            }

            if (siteInfo == null || siteService.isMember(siteInfo.getShortName(), AuthenticationUtil.getRunAsUser()))
            {

                if (!(Boolean)map.get(JSON_KEY_OVERRIDE))
                {
                    log.debug("Check for Concurent Users.");
                    if (googledocsService.hasConcurrentEditors(credential, nodeRef))
                    {
                        throw new ConcurrentEditorException("Node: " + nodeRef.toString()
                                                            + " has concurrent editors.");
                    }
                }

                // Should the content be removed from the users Google Drive Account
                boolean removeFromDrive = (map.get(JSON_KEY_REMOVEFROMDRIVE) != null) ? (Boolean)map.get(JSON_KEY_REMOVEFROMDRIVE)
                                                                                     : true;

                String contentType = googledocsService.getContentType(nodeRef);
                log.debug("NodeRef: " + nodeRef + "; ContentType: " + contentType);
                if (contentType.equals(GoogleDocsConstants.DOCUMENT_TYPE))
                {
                    if (googledocsService.isGoogleDocsLockOwner(nodeRef))
                    {
                        googledocsService.unlockNode(nodeRef);
                        googledocsService.getDocument(credential, nodeRef);

                        success = true; // TODO Make getDocument return boolean
                    }
                    else
                    {
                        throw new WebScriptException(HttpStatus.SC_FORBIDDEN, "Document is locked by another user.");
                    }
                }
                else if (contentType.equals(GoogleDocsConstants.SPREADSHEET_TYPE))
                {
                    if (googledocsService.isGoogleDocsLockOwner(nodeRef))
                    {
                        googledocsService.unlockNode(nodeRef);
                        googledocsService.getSpreadSheet(credential, nodeRef);

                        success = true; // TODO Make getSpreadsheet return boolean
                    }
                    else
                    {
                        throw new WebScriptException(HttpStatus.SC_FORBIDDEN, "Document is locked by another user.");
                    }
                }
                else if (contentType.equals(GoogleDocsConstants.PRESENTATION_TYPE))
                {
                    if (googledocsService.isGoogleDocsLockOwner(nodeRef))
                    {
                        googledocsService.unlockNode(nodeRef);
                        googledocsService.getPresentation(credential, nodeRef);

                        success = true; // TODO Make getPresentation return boolean
                    }
                    else
                    {
                        throw new WebScriptException(HttpStatus.SC_FORBIDDEN, "Document is locked by another user.");
                    }
                }
                else
                {
                    throw new WebScriptException(HttpStatus.SC_UNSUPPORTED_MEDIA_TYPE, "Content Type: " + contentType + " unknown.");
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

                log.debug("Version Node:" + nodeRef + "; Version Properties: " + versionProperties);
                Version version = versionService.createVersion(nodeRef, versionProperties);
                
                model.put(MODEL_VERSION, version.getVersionLabel());
                
                if (!removeFromDrive)
                {
                    googledocsService.lockNode(nodeRef);
                }
                else
                {
                    googledocsService.deleteContent(credential, nodeRef);
                }
                
            }
            else
            {
                throw new AccessDeniedException("Access Denied.  You do not have the appropriate permissions to perform this operation.");
            }

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
                throw new WebScriptException(gdse.getMessage(), gdse);
            }
        }
        catch (GoogleDocsRefreshTokenException gdrte)
        {
            throw new WebScriptException(HttpStatus.SC_BAD_GATEWAY, gdrte.getMessage(), gdrte);
        }
        catch (ConstraintException ce)
        {
            throw new WebScriptException(GoogleDocsConstants.STATUS_INTEGIRTY_VIOLATION, ce.getMessage(), ce);
        }
        catch (AccessDeniedException ade)
        {
            // This code will make changes after the rollback has occurred to clean up the node (remove the lock and the Google Docs
            // aspect
            AlfrescoTransactionSupport.bindListener(new TransactionListenerAdapter()
            {
                public void afterRollback()
                {
                    log.debug("Rollback Save to node: " + nodeRef);
                    transactionService.getRetryingTransactionHelper().doInTransaction(new RetryingTransactionCallback<Object>()
                    {
                        public Object execute()
                            throws Throwable
                        {
                            return AuthenticationUtil.runAsSystem(new AuthenticationUtil.RunAsWork<Object>()
                            {
                                public Object doWork()
                                    throws Exception
                                {
                                    googledocsService.unlockNode(nodeRef);
                                    googledocsService.unDecorateNode(nodeRef);

                                    // If the node was just created ('Create Content') it will have the temporary aspect and should
                                    // be completely removed.
                                    if (nodeService.hasAspect(nodeRef, ContentModel.ASPECT_TEMPORARY))
                                    {
                                        nodeService.deleteNode(nodeRef);
                                    }

                                    return null;
                                }
                            });
                        }
                    }, false, true);
                }
            });

            throw new WebScriptException(HttpStatus.SC_FORBIDDEN, ade.getMessage(), ade);
        }
        catch (ConcurrentEditorException e)
        {
            throw new WebScriptException(HttpStatus.SC_CONFLICT, e.getMessage(), e);
        }
        catch (Exception e)
        {
            throw new WebScriptException(HttpStatus.SC_INTERNAL_SERVER_ERROR, e.getMessage(), e);
        }

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
            log.debug("Parsed JSON: " + jsonStr);

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

                if (json.has(JSON_KEY_OVERRIDE))
                {
                    result.put(JSON_KEY_OVERRIDE, json.getBoolean(JSON_KEY_OVERRIDE));
                }
                else
                {
                    result.put(JSON_KEY_OVERRIDE, false);
                }

                if (nodeService.hasAspect(nodeRef, ContentModel.ASPECT_VERSIONABLE))
                {
                    result.put(JSON_KEY_MAJORVERSION, json.getBoolean(JSON_KEY_MAJORVERSION) ? VersionType.MAJOR
                                                                                            : VersionType.MINOR);
                    result.put(JSON_KEY_DESCRIPTION, json.getString(JSON_KEY_DESCRIPTION));
                }
                
                if (json.has(JSON_KEY_REMOVEFROMDRIVE))
                {
                    result.put(JSON_KEY_REMOVEFROMDRIVE, json.getBoolean(JSON_KEY_REMOVEFROMDRIVE));
                }  
            }
        }
        catch (final IOException ioe)
        {
            throw new WebScriptException(HttpStatus.SC_INTERNAL_SERVER_ERROR, ioe.getMessage(), ioe);
        }
        catch (final JSONException je)
        {
            throw new WebScriptException(HttpStatus.SC_BAD_REQUEST, "Unable to parse JSON: " + jsonStr, je);
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
