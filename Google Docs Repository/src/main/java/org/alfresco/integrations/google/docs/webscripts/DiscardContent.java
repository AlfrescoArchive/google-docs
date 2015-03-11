/**
 * Copyright (C) 2005-2012 Alfresco Software Limited.
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
import com.google.api.client.googleapis.json.GoogleJsonResponseException;
import com.google.api.services.drive.model.File;
import org.alfresco.integrations.google.docs.GoogleDocsModel;
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
import org.alfresco.service.cmr.repository.InvalidNodeRefException;
import org.alfresco.service.cmr.repository.NodeRef;
import org.alfresco.service.cmr.repository.NodeService;
import org.alfresco.service.cmr.security.AuthorityService;
import org.alfresco.service.cmr.security.PermissionService;
import org.alfresco.service.cmr.site.SiteInfo;
import org.alfresco.service.cmr.site.SiteService;
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


public class DiscardContent
    extends GoogleDocsWebScripts
{
    private static final Log    log               = LogFactory.getLog(DiscardContent.class);

    private GoogleDocsService   googledocsService;
    private NodeService         nodeService;
    private TransactionService  transactionService;
    private SiteService         siteService;

    //added because 4.2's SiteService does not have isSiteAdmin
    private PermissionService   permissionService;
    private AuthorityService    authorityService;

    private static final String JSON_KEY_NODEREF  = "nodeRef";
    private static final String JSON_KEY_OVERRIDE = "override";

    private static final String MODEL_SUCCESS     = "success";


    public void setGoogledocsService(GoogleDocsService googledocsService)
    {
        this.googledocsService = googledocsService;
    }


    public void setNodeService(NodeService nodeService)
    {
        this.nodeService = nodeService;
    }


    public void setTransactionService(TransactionService transactionService)
    {
        this.transactionService = transactionService;
    }


    public void setSiteService(SiteService siteService)
    {
        this.siteService = siteService;
    }


    public void setPermissionService(PermissionService permissionService)
    {
        this.permissionService = permissionService;
    }


    public void setAuthorityService(AuthorityService authorityService)
    {
        this.authorityService = authorityService;
    }


    @Override
    protected Map<String, Object> executeImpl(WebScriptRequest req, Status status, Cache cache)
    {
        getGoogleDocsServiceSubsystem();

        Map<String, Object> model = new HashMap<String, Object>();

        Map<String, Serializable> map = parseContent(req);
        final NodeRef nodeRef = (NodeRef)map.get(JSON_KEY_NODEREF);

        final Credential credential;
        boolean deleted = false;

        if (nodeService.hasAspect(nodeRef, GoogleDocsModel.ASPECT_EDITING_IN_GOOGLE))
        {
            try
            {
                //if the user is the lock owner run as the calling user else if the calling user is a Site Admin masquerade as the
                // Google Docs lock owner
                if (googledocsService.isGoogleDocsLockOwner(nodeRef))
                {
                    credential = googledocsService.getCredential();

                    if (!Boolean.valueOf(map.get(JSON_KEY_OVERRIDE).toString()))
                    {
                        SiteInfo siteInfo = siteService.getSite(nodeRef);
                        //The second part of this test maybe too exclusive.  What if the user has write permissions to the node
                        // but not membership in the containing site? Should the test just ask if the user has write permission
                        // to the node?
                        if (siteInfo == null || siteService.isMember(siteInfo.getShortName(), AuthenticationUtil.getRunAsUser()))
                        {
                            if (googledocsService.hasConcurrentEditors(credential, nodeRef))
                            {
                                throw new WebScriptException(HttpStatus.SC_CONFLICT, "Node: " + nodeRef.toString()
                                                                                     + " has concurrent editors.");
                            }
                        }
                        else
                        {
                            throw new AccessDeniedException("Access Denied.  You do not have the appropriate permissions to perform this operation.");
                        }
                    }

                        deleted = delete(credential, nodeRef);
                }
                else if (googledocsService.isSiteManager(nodeRef, AuthenticationUtil.getFullyAuthenticatedUser()))
                {
                    final String lockOwner = googledocsService.getGoogleDocsLockOwner(nodeRef);

                    if (lockOwner != null)
                    {
                        deleted = AuthenticationUtil.runAs(new AuthenticationUtil.RunAsWork<Boolean>()
                        {
                            @Override public Boolean doWork()
                                    throws Exception
                            {
                                boolean deletedAsUser = false;

                                try
                                {
                                    deletedAsUser = delete(null, nodeRef);
                                }
                                catch (GoogleDocsServiceException e)
                                {
                                    Throwable thrown = e.getCause();

                                    if (thrown != null && thrown instanceof GoogleJsonResponseException)
                                    {
                                        if (((GoogleJsonResponseException)thrown).getStatusCode() == HttpStatus.SC_UNAUTHORIZED)
                                        {
                                            log.info("Unable to access " + nodeRef +" as " + lockOwner);
                                            googledocsService.unlockNode(nodeRef);
                                            googledocsService.unDecorateNode(nodeRef);

                                            if (nodeService.hasAspect(nodeRef, ContentModel.ASPECT_TEMPORARY))
                                            {
                                                nodeService.deleteNode(nodeRef);
                                            }
                                        }

                                        deletedAsUser =  true;
                                    }
                                }
                                catch (IllegalStateException e)
                                {
                                    log.info("Unable to access " + nodeRef +" as " + lockOwner);
                                    googledocsService.unlockNode(nodeRef);
                                    googledocsService.unDecorateNode(nodeRef);

                                    if (nodeService.hasAspect(nodeRef, ContentModel.ASPECT_TEMPORARY))
                                    {
                                        nodeService.deleteNode(nodeRef);
                                    }

                                    deletedAsUser =  true;
                                }

                                return deletedAsUser;
                            }
                        }, lockOwner);
                    }
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
                throw new WebScriptException(HttpStatus.SC_BAD_GATEWAY, gdae.getMessage());
            }
            catch (GoogleDocsRefreshTokenException gdrte)
            {
                throw new WebScriptException(HttpStatus.SC_BAD_GATEWAY, gdrte.getMessage());
            }
            catch (GoogleDocsServiceException gdse)
            {
                if (gdse.getPassedStatusCode() == HttpStatus.SC_NOT_FOUND)
                {
                    // This code will make changes after the rollback has occurred to clean up the node: remove the lock and the Google
                    // Docs aspect. If it has the temporary aspect it will also remove the node from Alfresco
                    AlfrescoTransactionSupport.bindListener(new TransactionListenerAdapter()
                    {
                        public void afterCommit()
                        {
                            transactionService.getRetryingTransactionHelper().doInTransaction(new RetryingTransactionCallback<Object>()
                            {
                                public Object execute()
                                    throws Throwable
                                {
                                    AuthenticationUtil.runAsSystem(new AuthenticationUtil.RunAsWork<Object>()
                                    {
                                        public Object doWork()
                                            throws Exception
                                        {
                                            googledocsService.unlockNode(nodeRef);
                                            googledocsService.unDecorateNode(nodeRef);

                                            if (nodeService.hasAspect(nodeRef, ContentModel.ASPECT_TEMPORARY))
                                            {
                                                nodeService.deleteNode(nodeRef);
                                            }

                                           return null;
                                        }
                                    });

                                    return null;
                                }
                            }, false, true);
                        }
                    });
                    model.put(MODEL_SUCCESS, true);
                } else
                if (gdse.getPassedStatusCode() > -1)
                {
                    throw new WebScriptException(gdse.getPassedStatusCode(), gdse.getMessage());
                }
                else
                {
                    throw new WebScriptException(gdse.getMessage());
                }
            }
            catch (AccessDeniedException ade)
            {
                // This code will make changes after the rollback has occurred to clean up the node: remove the lock and the Google
                // Docs aspect. If it has the temporary aspect it will also remove the node from Alfresco
                AlfrescoTransactionSupport.bindListener(new TransactionListenerAdapter()
                {
                    public void afterRollback()
                    {
                        transactionService.getRetryingTransactionHelper().doInTransaction(new RetryingTransactionCallback<Object>()
                        {
                            public Object execute()
                                    throws Throwable
                            {
                                File file = googledocsService.getDriveFile(null, nodeRef);
                                googledocsService.unlockNode(nodeRef);
                                boolean deleted = googledocsService.deleteContent(null, nodeRef, file);

                                if (deleted)
                                {
                                    AuthenticationUtil.runAsSystem(new AuthenticationUtil.RunAsWork<Object>()
                                    {
                                        public Object doWork()
                                                throws Exception
                                        {
                                            googledocsService.unlockNode(nodeRef);
                                            googledocsService.unDecorateNode(nodeRef);

                                            if (nodeService.hasAspect(nodeRef, ContentModel.ASPECT_TEMPORARY))
                                            {
                                                nodeService.deleteNode(nodeRef);
                                            }

                                            return null;
                                        }
                                    });
                                }

                                return null;
                            }
                        }, false, true);
                    }
                });

                throw new WebScriptException(HttpStatus.SC_FORBIDDEN, ade.getMessage(), ade);
            }
        }
        else
        {
            throw new WebScriptException(HttpStatus.SC_NOT_ACCEPTABLE, "Missing Google Docs Aspect on " + nodeRef.toString());
        }

        return model;
    }


    /**
     * Delete the node from Google. If the node has the temporary aspect it is also removed from Alfresco.
     * 
     * @param nodeRef
     * @return
     * @throws InvalidNodeRefException
     * @throws IOException
     * @throws GoogleDocsServiceException
     * @throws GoogleDocsAuthenticationException
     * @throws GoogleDocsRefreshTokenException
     */
    private boolean delete(Credential credential, NodeRef nodeRef)
        throws InvalidNodeRefException,
            IOException,
            GoogleDocsServiceException,
            GoogleDocsAuthenticationException,
            GoogleDocsRefreshTokenException
    {
        File file = googledocsService.getDriveFile(credential, nodeRef);
        googledocsService.unlockNode(nodeRef);
        boolean deleted = googledocsService.deleteContent(credential, nodeRef, file);

        if (deleted)
        {
            if (nodeService.hasAspect(nodeRef, ContentModel.ASPECT_TEMPORARY))
            {
                nodeService.deleteNode(nodeRef);
            }
        }

        return deleted;
    }


    private Map<String, Serializable> parseContent(final WebScriptRequest req)
    {
        final Map<String, Serializable> result = new HashMap<String, Serializable>();
        Content content = req.getContent();
        String jsonStr = null;
        JSONObject json;

        try
        {
            if (content == null || content.getSize() == 0)
            {
                throw new WebScriptException(HttpStatus.SC_BAD_REQUEST, "No content sent with request.");
            }

            jsonStr = content.getContent();
            log.debug("Parsed JSON: " + jsonStr);

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

                if (json.has(JSON_KEY_OVERRIDE))
                {
                    result.put(JSON_KEY_OVERRIDE, json.getBoolean(JSON_KEY_OVERRIDE));
                }
                else
                {
                    result.put(JSON_KEY_OVERRIDE, false);
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
