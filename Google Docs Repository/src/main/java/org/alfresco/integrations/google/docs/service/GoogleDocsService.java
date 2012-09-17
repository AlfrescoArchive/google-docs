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

package org.alfresco.integrations.google.docs.service;


import java.io.IOException;
import java.util.Map;

import org.alfresco.integrations.google.docs.exceptions.GoogleDocsAuthenticationException;
import org.alfresco.integrations.google.docs.exceptions.GoogleDocsRefreshTokenException;
import org.alfresco.integrations.google.docs.exceptions.GoogleDocsServiceException;
import org.alfresco.integrations.google.docs.exceptions.GoogleDocsTypeException;
import org.alfresco.integrations.google.docs.exceptions.MustDowngradeFormatException;
import org.alfresco.integrations.google.docs.exceptions.MustUpgradeFormatException;
import org.alfresco.service.Auditable;
import org.alfresco.service.cmr.dictionary.ConstraintException;
import org.alfresco.service.cmr.repository.NodeRef;

import com.google.gdata.data.docs.DocumentListEntry;
import com.google.gdata.data.docs.MetadataEntry;


/**
 * @author Jared Ottley <jared.ottley@alfresco.com>
 */
public interface GoogleDocsService
{
    /**
     * Has the current user completed OAuth2 authentication against Google Docs
     * 
     * @return
     */
    @Auditable
    public boolean isAuthenticated();


    /**
     * Build the OAuth2 URL needed to authenticate the current user against Google Docs
     * 
     * @param state
     * @return
     */
    @Auditable(parameters = { "state" })
    public String getAuthenticateUrl(String state);


    /**
     * Complete the OAuth2 Dance for the current user, persisting the OAuth2 Tokens.
     * 
     * @param access_token
     * @return
     */
    @Auditable
    public boolean completeAuthentication(String access_token)
        throws GoogleDocsServiceException;

    /**
     * Is the Google Docs Integration enabled
     * @return
     */
    @Auditable
    public boolean isEnabled();
    
    @Auditable
    public MetadataEntry getUserMetadata()
        throws GoogleDocsAuthenticationException,
            GoogleDocsRefreshTokenException,
            GoogleDocsServiceException,
            IOException;


    /**
     * Create new Google Docs Document
     * 
     * @param nodeRef
     * @return
     */
    @Auditable(parameters = { "nodeRef" })
    public DocumentListEntry createDocument(NodeRef nodeRef)
        throws GoogleDocsServiceException,
            GoogleDocsTypeException,
            GoogleDocsAuthenticationException,
            GoogleDocsRefreshTokenException,
            IOException;


    /**
     * Create new Google Docs Presentation
     * 
     * @param nodeRef
     * @return
     */
    @Auditable(parameters = { "nodeRef" })
    public DocumentListEntry createPresentation(NodeRef nodeRef)
        throws GoogleDocsServiceException,
            GoogleDocsTypeException,
            GoogleDocsAuthenticationException,
            GoogleDocsRefreshTokenException,
            IOException;


    /**
     * Create new Google Docs SpreadSheet
     * 
     * @param nodeRef
     * @return
     */
    @Auditable(parameters = { "nodeRef" })
    public DocumentListEntry createSpreadSheet(NodeRef nodeRef)
        throws GoogleDocsServiceException,
            GoogleDocsTypeException,
            GoogleDocsAuthenticationException,
            GoogleDocsRefreshTokenException,
            IOException;


    /**
     * Apply/Update GoogleDocs Aspect
     * 
     * @param nodeRef
     * @param documentListEntry
     * @param newcontent If this is a new node, mark the content as temporary.
     */
    public void decorateNode(NodeRef nodeRef, DocumentListEntry documentListEntry, boolean newcontent);


    /**
     * Remove Google Docs Aspect
     * 
     * @param nodeRef
     */
    public void unDecorateNode(NodeRef nodeRef);


    /**
     * Can the mimetype be exported from Google Docs?
     * 
     * @param mimetype
     * @return
     * @throws MustUpgradeFormatException Thrown if the mimetype must be changed to a newer mimetype (ex. sxw -> odt)
     * @throws MustDowngradeFormatException Thrown if the mimetype must be changed to an older mimetype (ex. docx -> doc)
     */
    public boolean isExportable(String mimetype)
        throws MustUpgradeFormatException,
            MustDowngradeFormatException;


    /**
     * Can the mimetype be imported into Google Docs?
     * 
     * @param mimetype
     * @return
     */
    public boolean isImportable(String mimetype);

    
    /**
     * List of mimetypes that can be imported into Google Docs and the Google Doc Type
     * 
     * @return
     */
    public Map<String, String> getImportFormats();


    /**
     * Get the Google Doc Content type. A content type is a mapping of mimetype to Google Doc Type (document, spreadsheet or
     * presentation)
     * 
     * @param nodeRef
     * @return
     */
    public String getContentType(NodeRef nodeRef);


    /**
     * Retrieve the Google Doc Document associated to this node from Google Docs into the repository
     * 
     * @param nodeRef
     */
    @Auditable(parameters = { "nodeRef" })
    public void getDocument(NodeRef nodeRef)
        throws GoogleDocsAuthenticationException,
            GoogleDocsServiceException,
            GoogleDocsRefreshTokenException,
            IOException,
            ConstraintException;


    /**
     * Retrieve the Google Doc Spreadsheet associated to this node from Google Docs into the repository
     * 
     * @param nodeRef
     */
    @Auditable(parameters = { "nodeRef" })
    public void getSpreadSheet(NodeRef nodeRef)
        throws GoogleDocsAuthenticationException,
            GoogleDocsServiceException,
            GoogleDocsRefreshTokenException,
            IOException,
            ConstraintException;


    /**
     * Retrieve the Google Doc Presentation associated to this node from Google Docs into the repository
     * 
     * @param nodeRef
     */
    @Auditable(parameters = { "nodeRef" })
    public void getPresentation(NodeRef nodeRef)
        throws GoogleDocsAuthenticationException,
            GoogleDocsServiceException,
            GoogleDocsRefreshTokenException,
            IOException,
            ConstraintException;


    /**
     * Upload node to Google Docs
     * 
     * @param nodeRef
     * @return
     */
    @Auditable(parameters = { "nodeRef" })
    public DocumentListEntry uploadFile(NodeRef nodeRef)
        throws GoogleDocsAuthenticationException,
            GoogleDocsServiceException,
            GoogleDocsRefreshTokenException,
            IOException;


    /**
     * @param resourceID
     * @return
     * @throws IOException
     * @throws ServiceException
     * @throws GoogleDocsAuthenticationException
     * @throws GoogleDocsRefreshTokenException
     */
    @Auditable(parameters = { "resourceId" })
    public DocumentListEntry getDocumentListEntry(String resourceID)
        throws IOException,
            GoogleDocsServiceException,
            GoogleDocsAuthenticationException,
            GoogleDocsRefreshTokenException;


    /**
     * @param nodeRef
     * @param documentListEntry
     * @return
     * @throws GoogleDocsAuthenticationException
     * @throws GoogleDocsServiceException
     * @throws GoogleDocsRefreshTokenException
     * @throws IOException
     */
    @Auditable(parameters = { "nodeRef", "documentListEntry" })
    public boolean deleteContent(NodeRef nodeRef, DocumentListEntry documentListEntry)
        throws GoogleDocsAuthenticationException,
            GoogleDocsServiceException,
            GoogleDocsRefreshTokenException,
            IOException;


    /**
     * Google Doc has Concurrent Editors
     * 
     * @param nodeRef
     * @return
     */
    @Auditable(parameters = { "nodeRef" })
    public boolean hasConcurrentEditors(NodeRef nodeRef)
        throws GoogleDocsAuthenticationException,
            GoogleDocsRefreshTokenException,
            GoogleDocsServiceException,
            IOException;


    /**
     * @param noderef
     */
    @Auditable(parameters = { "nodeRef" })
    public void lockNode(NodeRef nodeRef);


    /**
     * @param noderef
     */
    @Auditable(parameters = { "nodeRef" })
    public void unlockNode(NodeRef nodeRef);


    /**
     * @param nodeRef
     * @return
     */
    @Auditable(parameters = { "nodeRef" })
    public boolean isLockedByGoogleDocs(NodeRef nodeRef);


    /**
     * @param nodeRef
     * @return
     */
    @Auditable(parameters = { "nodeRef" })
    public boolean isGoogleDocsLockOwner(NodeRef nodeRef);
}
