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
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import com.google.api.client.auth.oauth2.Credential;
import com.google.api.services.drive.model.File;
import org.alfresco.integrations.google.docs.GoogleDocsConstants;
import org.alfresco.integrations.google.docs.exceptions.GoogleDocsAuthenticationException;
import org.alfresco.integrations.google.docs.exceptions.GoogleDocsRefreshTokenException;
import org.alfresco.integrations.google.docs.exceptions.GoogleDocsServiceException;
import org.alfresco.integrations.google.docs.exceptions.GoogleDocsTypeException;
import org.alfresco.integrations.google.docs.service.GoogleDocsService;
import org.alfresco.integrations.google.docs.utils.FileNameUtil;
import org.alfresco.model.ContentModel;
import org.alfresco.repo.management.subsystems.ApplicationContextFactory;
import org.alfresco.service.cmr.model.FileFolderService;
import org.alfresco.service.cmr.model.FileNotFoundException;
import org.alfresco.service.cmr.repository.NodeRef;
import org.alfresco.service.namespace.NamespaceService;
import org.alfresco.service.namespace.QName;
import org.apache.commons.httpclient.HttpStatus;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.extensions.webscripts.Cache;
import org.springframework.extensions.webscripts.Status;
import org.springframework.extensions.webscripts.WebScriptException;
import org.springframework.extensions.webscripts.WebScriptRequest;

/**
 * @author Jared Ottley <jared.ottley@alfresco.com>
 */
public class CreateContent
    extends GoogleDocsWebScripts
{
    private static final Log    log           = LogFactory.getLog(CreateContent.class);

    private final static String FILENAMEUTIL     = "fileNameUtil";

    private GoogleDocsService   googledocsService;
    private FileFolderService   fileFolderService;

    private FileNameUtil        fileNameUtil;

    private final static String PARAM_TYPE       = "contenttype";
    private final static String PARAM_PARENT     = "parent";

    private final static String MODEL_NODEREF    = "nodeRef";
    private final static String MODEL_EDITOR_URL = "editorUrl";


    public void setGoogledocsService(GoogleDocsService googledocsService)
    {
        this.googledocsService = googledocsService;
    }

    public void setFileFolderService(FileFolderService fileFolderService)
    {
        this.fileFolderService = fileFolderService;
    }

    public void setFileNameUtil(FileNameUtil fileNameUtil)
    {
        this.fileNameUtil = fileNameUtil;
    }


    @Override
    protected Map<String, Object> executeImpl(WebScriptRequest req, Status status, Cache cache)
    {
        // Set Service Beans
        this.getGoogleDocsServiceSubsystem();

        Map<String, Object> model = new HashMap<String, Object>();

        if (googledocsService.isEnabled())
        {

            String contentType = req.getParameter(PARAM_TYPE);
            NodeRef parentNodeRef = new NodeRef(req.getParameter(PARAM_PARENT));

            log.debug("ContentType: " + contentType + "; Parent: " + parentNodeRef);

            NodeRef newNode = null;
            File file = null;
            try
            {
                Credential credential = googledocsService.getCredential();
                if (contentType.equals(GoogleDocsConstants.DOCUMENT_TYPE))
                {
                    newNode = createFile(parentNodeRef, contentType, GoogleDocsConstants.MIMETYPE_DOCUMENT);
                    file = googledocsService.createDocument(credential, newNode);
                }
                else if (contentType.equals(GoogleDocsConstants.SPREADSHEET_TYPE))
                {
                    newNode = createFile(parentNodeRef, contentType, GoogleDocsConstants.MIMETYPE_SPREADSHEET);
                    file = googledocsService.createSpreadSheet(credential, newNode);
                }
                else if (contentType.equals(GoogleDocsConstants.PRESENTATION_TYPE))
                {
                    newNode = createFile(parentNodeRef, contentType, GoogleDocsConstants.MIMETYPE_PRESENTATION);
                    file = googledocsService.createPresentation(credential, newNode);
                }
                else
                {
                    throw new WebScriptException(HttpStatus.SC_UNSUPPORTED_MEDIA_TYPE, "Content Type Not Found.");
                }

                googledocsService.decorateNode(newNode, file, googledocsService.getLatestRevision(credential, file), true);

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
            catch (GoogleDocsTypeException gdte)
            {
                throw new WebScriptException(HttpStatus.SC_UNSUPPORTED_MEDIA_TYPE, gdte.getMessage());
            }
            catch (GoogleDocsAuthenticationException gdae)
            {
                throw new WebScriptException(HttpStatus.SC_BAD_GATEWAY, gdae.getMessage());
            }
            catch (GoogleDocsRefreshTokenException gdrte)
            {
                throw new WebScriptException(HttpStatus.SC_BAD_GATEWAY, gdrte.getMessage());
            }
            catch (IOException ioe)
            {
                throw new WebScriptException(HttpStatus.SC_INTERNAL_SERVER_ERROR, ioe.getMessage(), ioe);
            }
            catch (Exception e)
            {
                throw new WebScriptException(HttpStatus.SC_INTERNAL_SERVER_ERROR, e.getMessage(), e);
            }

            googledocsService.lockNode(newNode);

            model.put(MODEL_NODEREF, newNode.toString());
            model.put(MODEL_EDITOR_URL, file.getAlternateLink());

        }
        else
        {
            throw new WebScriptException(HttpStatus.SC_SERVICE_UNAVAILABLE, "Google Docs Disabled");
        }

        return model;
    }

    /**
     * Create a new content item for a document, spreadsheet or presentation which is to be edited in Google Docs
     *
     * <p>The name of the file is generated automatically, based on the type of content. In the event of a clash with
     * an existing file, the file name will have a numeric suffix placed on the end of it before the file extension,
     * which will be incremented until a valid name is found.</p>
     *
     * @param parentNodeRef NodeRef identifying the folder where the content will be created
     * @param contentType   The type of content to be created, one of 'document', 'spreadsheet' or 'presentation'
     * @param mimetype  The mimetype of the new content item, used to determine the file extension to add
     * @return  A FileInfo object representing the new content item. Call fileInfo.getNodeRef() to get the nodeRef
     */
    private NodeRef createFile(final NodeRef parentNodeRef, final String contentType, final String mimetype)
    {
        String baseName = getNewFileName(contentType), fileExt = fileNameUtil.getExtension(mimetype);
        final StringBuffer sb = new StringBuffer(baseName);
        if (fileExt != null && !fileExt.equals(""))
        {
            sb.append(".").append(fileExt);
        }
        int i = 0, maxCount = 1000; // Limit the damage should something go horribly wrong and a FileExistsException is always thrown

        while (i <= maxCount)
        {
            List<String> parts = new ArrayList<String>(1);
            parts.add(QName.createQName(NamespaceService.CONTENT_MODEL_1_0_URI, sb.toString()).toPrefixString());
            try
            {
                if (fileFolderService.resolveNamePath(parentNodeRef, parts, false) == null)
                {
                    return fileFolderService.create(parentNodeRef, sb.toString(), ContentModel.TYPE_CONTENT).getNodeRef();
                }
                else
                {
                    log.debug("Filename " + sb.toString() + " already exists");
                    String name = fileNameUtil.incrementFileName(sb.toString());
                    sb.replace(0, sb.length(), name);
                    if (log.isDebugEnabled())
                        log.debug("new file name " + sb.toString());
                }
            }
            catch (FileNotFoundException e) // We should never catch this because we set mustExist=false
            {
                throw new WebScriptException(HttpStatus.SC_INTERNAL_SERVER_ERROR, "Unexpected FileNotFoundException", e);
            }
            i++;
        }
        throw new WebScriptException(HttpStatus.SC_CONFLICT, "Too many untitled files. Try renaming some existing documents.");
    }


    /**
     * Get the default new content name
     * 
     * @param type
     * @return
     */
    private static String getNewFileName(String type)
    {
        String name = null;
        if (type.equals(GoogleDocsConstants.DOCUMENT_TYPE))
        {
            name = GoogleDocsConstants.NEW_DOCUMENT_NAME;
        }
        else if (type.equals(GoogleDocsConstants.SPREADSHEET_TYPE))
        {
            name = GoogleDocsConstants.NEW_SPREADSHEET_NAME;
        }
        else if (type.equals(GoogleDocsConstants.PRESENTATION_TYPE))
        {
            name = GoogleDocsConstants.NEW_PRESENTATION_NAME;
        }

        return name;
    }


    protected void getGoogleDocsServiceSubsystem()
    {
        ApplicationContextFactory subsystem = (ApplicationContextFactory)applicationContext.getBean(GOOGLEDOCS_DEFAULT_SUBSYSTEM);
        ConfigurableApplicationContext childContext = (ConfigurableApplicationContext)subsystem.getApplicationContext();
        setGoogledocsService((GoogleDocsService)childContext.getBean(GOOGLEDOCSSERVICE));
        setFileNameUtil((FileNameUtil)childContext.getBean(FILENAMEUTIL));
    }
}
