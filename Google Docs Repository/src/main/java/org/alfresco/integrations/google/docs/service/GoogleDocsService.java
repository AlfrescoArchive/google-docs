package org.alfresco.integrations.google.docs.service;

import java.util.ArrayList;

import org.alfresco.integrations.google.docs.exceptions.MustDowngradeFormatException;
import org.alfresco.integrations.google.docs.exceptions.MustUpgradeFormatException;
import org.alfresco.service.Auditable;
import org.alfresco.service.cmr.repository.NodeRef;

import com.google.gdata.data.docs.DocumentListEntry;
import com.google.gdata.data.docs.MetadataEntry;

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
    @Auditable(parameters = {"state"})
    public String getAuthenticateUrl(String state);
    
    /**
     * Complete the OAuth2 Dance for the current user, persisting the OAuth2 Tokens.
     * 
     * @param access_token
     * @return
     */
    @Auditable
    public boolean completeAuthentication(String access_token);
    
    @Auditable
    public MetadataEntry getUserMetadata();
    
    /**
     * Create new Google Docs Document
     * 
     * @param nodeRef
     * @return
     */
    @Auditable(parameters = {"nodeRef"})
    public DocumentListEntry createDocument(NodeRef nodeRef);
    
    /**
     * Create new Google Docs Presentation
     * 
     * @param nodeRef
     * @return
     */
    @Auditable(parameters = {"nodeRef"})
    public DocumentListEntry createPresentation(NodeRef nodeRef);
    
    /**
     * Create new Google Docs SpreadSheet
     * 
     * @param nodeRef
     * @return
     */
    @Auditable(parameters = {"nodeRef"})
    public DocumentListEntry createSpreadSheet(NodeRef nodeRef);
    
    /**
     * Apply/Update GoogleDocs Aspect
     * 
     * @param nodeRef
     * @param documentListEntry
     * @param newcontent If this is a new node, mark the content as temporary.
     */
    public void decorateNode(NodeRef nodeRef, DocumentListEntry documentListEntry, boolean newcontent);
    
    /**
     * Can the mimetype be exported from Google Docs?
     * 
     * 
     * @param mimetype
     * @return
     * @throws MustUpgradeFormatException Thrown if the mimetype must be changed to a newer mimetype (ex. sxw -> odt)
     * @throws MustDowngradeFormatException Thrown if the mimetype must be changed to an older mimetype (ex. docx -> doc)
     */
    public boolean isExportable(String mimetype) throws MustUpgradeFormatException, MustDowngradeFormatException;

    /**
     * Can the mimetype be imported into Google Docs?
     * 
     * @param mimetype
     * @return
     */
    public boolean isImportable(String mimetype);
    
    /**
     * List of mimetypes that can be imported into Google Docs.
     * 
     * @return
     */
    public ArrayList<String> getImportFormatsList();
    
    /**
     * Get the Google Doc Content type.
     * A content type is a mapping of mimetype to Google Doc Type (document, spreadsheet or presentation)
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
    @Auditable(parameters = {"nodeRef"})
    public void getDocument(NodeRef nodeRef);
    
    /**
     * Retrieve the Google Doc Spreadsheet associated to this node from Google Docs into the repository
     * 
     * @param nodeRef
     */
    @Auditable(parameters = {"nodeRef"})
    public void getSpreadSheet(NodeRef nodeRef);
    
    /**
     * Retrieve the Google Doc Presentation associated to this node from Google Docs into the repository
     * 
     * @param nodeRef
     */
    @Auditable(parameters = {"nodeRef"})
    public void getPresentation(NodeRef nodeRef);
    
    /**
     * Upload node to Google Docs
     * 
     * @param nodeRef
     * @return
     */
    @Auditable(parameters = {"nodeRef"})
    public DocumentListEntry uploadFile(NodeRef nodeRef);
    
    /**
     * Google Doc has Concurrent Editors
     * 
     * @param nodeRef
     * @return
     */
    @Auditable(parameters = {"nodeRef"})
    public boolean hasConcurrentEditors(NodeRef nodeRef);
}
