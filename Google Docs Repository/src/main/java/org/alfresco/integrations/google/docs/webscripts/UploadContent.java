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

import org.alfresco.integrations.google.docs.exceptions.GoogleDocsAuthenticationException;
import org.alfresco.integrations.google.docs.exceptions.GoogleDocsRefreshTokenException;
import org.alfresco.integrations.google.docs.exceptions.GoogleDocsServiceException;
import org.alfresco.integrations.google.docs.service.GoogleDocsService;
import org.alfresco.model.ContentModel;
import org.alfresco.repo.version.Version2Model;
import org.alfresco.service.cmr.repository.NodeRef;
import org.alfresco.service.cmr.repository.NodeService;
import org.alfresco.service.cmr.version.VersionService;
import org.alfresco.service.cmr.version.VersionType;
import org.apache.commons.httpclient.HttpStatus;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.springframework.extensions.webscripts.Cache;
import org.springframework.extensions.webscripts.Status;
import org.springframework.extensions.webscripts.WebScriptException;
import org.springframework.extensions.webscripts.WebScriptRequest;

import com.google.gdata.data.docs.DocumentListEntry;


/**
 * @author Jared Ottley <jared.ottley@alfresco.com>
 */
public class UploadContent
    extends GoogleDocsWebScripts
{
    private static final Log    log           = LogFactory.getLog(UploadContent.class);

    private GoogleDocsService   googledocsService;
    private NodeService         nodeService;
    private VersionService      versionService;

    private static final String PARAM_NODEREF = "nodeRef";
    private static final String MODEL_NODEREF = "nodeRef";


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
        getGoogleDocsServiceSubsystem();

        Map<String, Object> model = new HashMap<String, Object>();

        if (googledocsService.isEnabled())
        {
        
        String param_nodeRef = req.getParameter(PARAM_NODEREF);
        NodeRef nodeRef = new NodeRef(param_nodeRef);

        DocumentListEntry entry;
        try
        {
            entry = googledocsService.uploadFile(nodeRef);
            log.debug(nodeRef + " Uploaded to Google.");

            // If this is a non-cloud instance of Alfresco, we need to make the
            // node versionable before we start working on it. We want the the
            // version component to be triggered on save. The versionable aspect
            // is only added if this is existing content, not if it was just
            // created where the document is the initial version when saved
            if (!nodeService.hasAspect(nodeRef, ContentModel.ASPECT_TEMPORARY)
                && !nodeService.hasAspect(nodeRef, ContentModel.ASPECT_VERSIONABLE))
            {
                Map<String, Serializable> versionProperties = new HashMap<String, Serializable>();
                versionProperties.put(Version2Model.PROP_VERSION_TYPE, VersionType.MAJOR);

                nodeService.setProperty(nodeRef, ContentModel.PROP_AUTO_VERSION, true);
                // autoVersionOnUpdateProps now set to false to follow Share upload scripts (fixes GOOGLEDOCS-111)
                nodeService.setProperty(nodeRef, ContentModel.PROP_AUTO_VERSION_PROPS, false);

                log.debug("Version Node:" + nodeRef + "; Version Properties: " + versionProperties);
                versionService.createVersion(nodeRef, versionProperties);
            }

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
        
        }
        else
        {
            throw new WebScriptException(HttpStatus.SC_SERVICE_UNAVAILABLE, "Google Docs Disabled");
        }

        return model;
    }
}
