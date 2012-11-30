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


import java.io.File;
import java.io.IOException;
import java.io.Serializable;
import java.net.URL;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Collections;
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.alfresco.integrations.google.docs.GoogleDocsConstants;
import org.alfresco.integrations.google.docs.GoogleDocsModel;
import org.alfresco.integrations.google.docs.exceptions.GoogleDocsAuthenticationException;
import org.alfresco.integrations.google.docs.exceptions.GoogleDocsRefreshTokenException;
import org.alfresco.integrations.google.docs.exceptions.GoogleDocsServiceException;
import org.alfresco.integrations.google.docs.exceptions.GoogleDocsTypeException;
import org.alfresco.integrations.google.docs.exceptions.MustDowngradeFormatException;
import org.alfresco.integrations.google.docs.exceptions.MustUpgradeFormatException;
import org.alfresco.integrations.google.docs.utils.FileNameUtil;
import org.alfresco.integrations.google.docs.utils.RevisionEntryComparator;
import org.alfresco.model.ContentModel;
import org.alfresco.query.CannedQueryPageDetails;
import org.alfresco.query.PagingRequest;
import org.alfresco.query.PagingResults;
import org.alfresco.repo.policy.BehaviourFilter;
import org.alfresco.repo.security.authentication.AuthenticationUtil;
import org.alfresco.repo.tenant.TenantService;
import org.alfresco.service.cmr.activities.ActivityService;
import org.alfresco.service.cmr.dictionary.ConstraintDefinition;
import org.alfresco.service.cmr.dictionary.ConstraintException;
import org.alfresco.service.cmr.dictionary.DictionaryService;
import org.alfresco.service.cmr.lock.LockService;
import org.alfresco.service.cmr.lock.LockStatus;
import org.alfresco.service.cmr.lock.LockType;
import org.alfresco.service.cmr.model.FileFolderService;
import org.alfresco.service.cmr.model.FileInfo;
import org.alfresco.service.cmr.oauth2.OAuth2CredentialsStoreService;
import org.alfresco.service.cmr.remotecredentials.OAuth2CredentialsInfo;
import org.alfresco.service.cmr.remoteticket.NoSuchSystemException;
import org.alfresco.service.cmr.repository.ContentReader;
import org.alfresco.service.cmr.repository.ContentWriter;
import org.alfresco.service.cmr.repository.MimetypeService;
import org.alfresco.service.cmr.repository.NodeRef;
import org.alfresco.service.cmr.repository.NodeService;
import org.alfresco.service.cmr.security.PersonService;
import org.alfresco.service.cmr.security.PersonService.PersonInfo;
import org.alfresco.service.cmr.site.SiteService;
import org.alfresco.service.namespace.NamespaceService;
import org.alfresco.service.namespace.QName;
import org.alfresco.util.Pair;
import org.alfresco.util.TempFileProvider;
import org.apache.commons.httpclient.HttpStatus;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.json.JSONException;
import org.json.JSONObject;
import org.springframework.core.io.Resource;
import org.springframework.social.ApiException;
import org.springframework.social.connect.Connection;
import org.springframework.social.google.docs.api.GoogleDocs;
import org.springframework.social.google.docs.connect.GoogleDocsConnectionFactory;
import org.springframework.social.oauth2.AccessGrant;
import org.springframework.social.oauth2.GrantType;
import org.springframework.social.oauth2.OAuth2Parameters;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.HttpClientErrorException;

import com.google.gdata.client.docs.DocsService;
import com.google.gdata.client.media.ResumableGDataFileUploader;
import com.google.gdata.client.uploader.ResumableHttpFileUploader.UploadState;
import com.google.gdata.data.MediaContent;
import com.google.gdata.data.PlainTextConstruct;
import com.google.gdata.data.docs.DocumentEntry;
import com.google.gdata.data.docs.DocumentListEntry;
import com.google.gdata.data.docs.MetadataEntry;
import com.google.gdata.data.docs.PresentationEntry;
import com.google.gdata.data.docs.RevisionEntry;
import com.google.gdata.data.docs.RevisionFeed;
import com.google.gdata.data.docs.SpreadsheetEntry;
import com.google.gdata.data.media.MediaFileSource;
import com.google.gdata.data.media.MediaSource;
import com.google.gdata.util.ServiceException;


/**
 * @author Jared Ottley <jared.ottley@alfresco.com>
 */
/**
 * @author jottley
 *
 */
public class GoogleDocsServiceImpl
    implements GoogleDocsService
{
    private static final Log                 log               = LogFactory.getLog(GoogleDocsServiceImpl.class);

    // Services
    private OAuth2CredentialsStoreService    oauth2CredentialsStoreService;
    private GoogleDocsConnectionFactory      connectionFactory;
    private FileFolderService                fileFolderService;
    private NodeService                      nodeService;
    private LockService                      lockservice;
    private MimetypeService                  mimetypeService;
    private BehaviourFilter                  behaviourFilter;
    private ActivityService                  activityService;
    private SiteService                      siteService;
    private TenantService                    tenantService;
    private PersonService                    personService;

    private DictionaryService                dictionaryService;
    private FileNameUtil                     filenameUtil;

    // Property Mappings
    private Map<String, String>              importFormats     = new HashMap<String, String>();
    private Map<String, Map<String, String>> exportFormats     = new HashMap<String, Map<String, String>>();
    private Map<String, String>              upgradeMappings   = new HashMap<String, String>();
    private Map<String, String>              downgradeMappings = new HashMap<String, String>();

    // New Content
    private Resource                         newDocument;
    private Resource                         newSpreadsheet;
    private Resource                         newPresentation;

    // Time (in seconds) between last edit and now to consider edits as
    // concurrent
    private int                              idleThreshold     = 0;

    private boolean                          enabled           = true;

    // Activities
    private static final String              FILE_ADDED        = "org.alfresco.documentlibrary.file-added";
    private static final String              FILE_UPDATED      = "org.alfresco.documentlibrary.file-updated";


    public void setImportFormats(Map<String, String> importFormats)
    {
        this.importFormats = importFormats;
    }


    public void setExportFormats(Map<String, Map<String, String>> exportFormats)
    {
        this.exportFormats = exportFormats;
    }


    public void setUpgradeMappings(Map<String, String> upgradeMappings)
    {
        this.upgradeMappings = upgradeMappings;
    }


    public void setDowngradeMappings(Map<String, String> downgradeMappings)
    {
        this.downgradeMappings = downgradeMappings;
    }


    public void setOauth2CredentialsStoreService(OAuth2CredentialsStoreService oauth2CredentialsStoreService)
    {
        this.oauth2CredentialsStoreService = oauth2CredentialsStoreService;
    }


    public void setConnectionFactory(GoogleDocsConnectionFactory connectionFactory)
    {
        this.connectionFactory = connectionFactory;
    }


    public void setFileFolderService(FileFolderService fileFolderService)
    {
        this.fileFolderService = fileFolderService;
    }


    public void setNodeService(NodeService nodeService)
    {
        this.nodeService = nodeService;
    }


    public void setLockService(LockService lockService)
    {
        this.lockservice = lockService;
    }


    public void setMimetypeService(MimetypeService mimetypeService)
    {
        this.mimetypeService = mimetypeService;
    }


    public void setBehaviourFilter(BehaviourFilter behaviourFilter)
    {
        this.behaviourFilter = behaviourFilter;
    }


    public void setActivityService(ActivityService activityService)
    {
        this.activityService = activityService;
    }


    public void setSiteService(SiteService siteService)
    {
        this.siteService = siteService;
    }


    public void setTenantService(TenantService tenantService)
    {
        this.tenantService = tenantService;
    }


    public void setPersonService(PersonService personService)
    {
        this.personService = personService;
    }


    public void setDictionaryService(DictionaryService dictionaryService)
    {
        this.dictionaryService = dictionaryService;
    }


    public void setFileNameUtil(FileNameUtil fileNameUtil)
    {
        this.filenameUtil = fileNameUtil;
    }


    public Map<String, String> getImportFormats()
    {
        return importFormats;
    }


    public void setNewDocument(Resource newDocument)
    {
        this.newDocument = newDocument;
    }


    public void setNewSpreadsheet(Resource newSpreadsheet)
    {
        this.newSpreadsheet = newSpreadsheet;
    }


    public void setNewPresentation(Resource newPresentation)
    {
        this.newPresentation = newPresentation;
    }


    public void setIdleThreshold(int idleThreshold)
    {
        this.idleThreshold = idleThreshold;
    }


    public void setEnabled(boolean enabled)
    {
        this.enabled = enabled;
    }


    public boolean isEnabled()
    {
        return enabled;
    }


    public boolean isImportable(String mimetype)
    {
        return importFormats.containsKey(mimetype);
    }


    private String getImportType(String mimetype)
    {
        return importFormats.get(mimetype);
    }


    public boolean isExportable(String mimetype)
        throws MustUpgradeFormatException,
            MustDowngradeFormatException
    {
        if (isUpgrade(mimetype))
        {
            throw new MustUpgradeFormatException();
        }
        else if (isDownGrade(mimetype))
        {
            throw new MustDowngradeFormatException();
        }

        String type = getImportType(mimetype);
        Set<String> exportMimetypes = getExportableMimeTypes(type);

        return exportMimetypes.contains(mimetype);
    }


    private Set<String> getExportableMimeTypes(String type)
    {
        Set<String> mimetypes = new HashSet<String>();

        if (exportFormats.containsKey(type))
        {
            mimetypes = exportFormats.get(type).keySet();
        }

        return mimetypes;
    }


    private boolean isUpgrade(String mimetype)
    {
        return upgradeMappings.containsKey(mimetype);
    }


    private boolean isDownGrade(String mimetype)
    {
        return downgradeMappings.containsKey(mimetype);
    }


    public String getContentType(NodeRef nodeRef)
    {
        String contentType = null;

        String mimetype = fileFolderService.getFileInfo(nodeRef).getContentData().getMimetype();

        contentType = importFormats.get(mimetype);

        return contentType;
    }


    private String getExportFormat(String contentType, String mimeType)
    {
        String exportFormat = null;

        mimeType = validateMimeType(mimeType);

        if (exportFormats.containsKey(contentType))
        {
            exportFormat = exportFormats.get(contentType).get(mimeType);
        }

        return exportFormat;
    }


    /**
     * @param mimeType Mimetype of the Node
     * @return If the Document must be returned in as a different type, returns the new type
     */
    private String validateMimeType(String mimeType)
    {

        if (isDownGrade(mimeType))
        {
            mimeType = downgradeMappings.get(mimeType);
        }
        else if (isUpgrade(mimeType))
        {
            mimeType = upgradeMappings.get(mimeType);
        }

        return mimeType;
    }


    private Connection<GoogleDocs> getConnection()
        throws GoogleDocsAuthenticationException,
            GoogleDocsRefreshTokenException,
            GoogleDocsServiceException
    {
        Connection<GoogleDocs> connection = null;

        OAuth2CredentialsInfo credentialInfo = oauth2CredentialsStoreService.getPersonalOAuth2Credentials(GoogleDocsConstants.REMOTE_SYSTEM);

        if (credentialInfo != null)
        {
            log.debug("OAuth Access Token Exists");
            AccessGrant accessGrant = new AccessGrant(credentialInfo.getOAuthAccessToken());

            try
            {
                log.debug("Attempt to create OAuth Connection");
                connection = connectionFactory.createConnection(accessGrant);
            }
            catch (ApiException apie)
            {
                if (apie.getCause() instanceof ServiceException)
                {
                    ServiceException se = (ServiceException)apie.getCause();
                    if (se.getHttpErrorCodeOverride() == HttpStatus.SC_UNAUTHORIZED)
                    {
                        try
                        {
                            accessGrant = refreshAccessToken();
                            connection = connectionFactory.createConnection(accessGrant);
                        }
                        catch (GoogleDocsRefreshTokenException gdrte)
                        {
                            throw gdrte;
                        }
                        catch (GoogleDocsServiceException gdse)
                        {
                            throw gdse;
                        }
                    }
                }
            }
        }

        log.debug("Connection Created");
        return connection;
    }


    public boolean isAuthenticated()
    {
        boolean authenticated = false;

        OAuth2CredentialsInfo credentialInfo = oauth2CredentialsStoreService.getPersonalOAuth2Credentials(GoogleDocsConstants.REMOTE_SYSTEM);

        if (credentialInfo != null)
        {
            authenticated = true;
        }

        log.debug("Authenticated: " + authenticated);
        return authenticated;
    }


    public String getAuthenticateUrl(String state)
    {
        String authenticateUrl = null;

        if (state != null)
        {

            /*
             * When we change to spring social 1.0.2 OAuth2Parameters will need to be updated OAuth2Parameters parameters = new
             * OAuth2Parameters(); parameters.setRedirectUri(REDIRECT_URI); parameters.setScope(SCOPE); parameters.setState(state);
             */

            MultiValueMap<String, String> additionalParameters = new LinkedMultiValueMap<String, String>(1);
            additionalParameters.add("access_type", "offline");

            OAuth2Parameters parameters = new OAuth2Parameters(GoogleDocsConstants.REDIRECT_URI, GoogleDocsConstants.SCOPE, state, additionalParameters);
            parameters.getAdditionalParameters();
            authenticateUrl = connectionFactory.getOAuthOperations().buildAuthenticateUrl(GrantType.AUTHORIZATION_CODE, parameters);

        }

        log.debug("Authentication URL: " + authenticateUrl);
        return authenticateUrl;
    }


    public boolean completeAuthentication(String access_token)
        throws GoogleDocsServiceException
    {
        boolean authenticationComplete = false;

        AccessGrant accessGrant = connectionFactory.getOAuthOperations().exchangeForAccess(access_token, GoogleDocsConstants.REDIRECT_URI, null);

        Date expiresIn = null;

        if (accessGrant.getExpireTime() != null)
        {
            if (accessGrant.getExpireTime() > 0L)
            {
                expiresIn = new Date(new Date().getTime() + accessGrant.getExpireTime());
            }
        }

        try
        {
            // If this is a reauth....we may not get back the refresh token. We
            // need to make sure it is persisted across the "refresh".
            if (accessGrant.getRefreshToken() == null)
            {
                log.debug("Missing Refresh Token");

                OAuth2CredentialsInfo credentialInfo = oauth2CredentialsStoreService.getPersonalOAuth2Credentials(GoogleDocsConstants.REMOTE_SYSTEM);
                // In the "rare" case that no refresh token is returned and the
                // users credentials are no longer there we need to skip this
                // next check
                if (credentialInfo != null)
                {
                    // If there is a persisted refresh ticket...add it to the
                    // accessGrant so that it is persisted across the update
                    if (credentialInfo.getOAuthRefreshToken() != null)
                    {
                        accessGrant = new AccessGrant(accessGrant.getAccessToken(), accessGrant.getScope(), credentialInfo.getOAuthRefreshToken(), accessGrant.getExpireTime().intValue());
                        log.debug("Persisting Refresh Token across reauth");
                    }
                }
            }

            oauth2CredentialsStoreService.storePersonalOAuth2Credentials(GoogleDocsConstants.REMOTE_SYSTEM, accessGrant.getAccessToken(), accessGrant.getRefreshToken(), expiresIn, new Date());

            authenticationComplete = true;
        }
        catch (NoSuchSystemException nsse)
        {
            throw new GoogleDocsServiceException(nsse.getMessage());
        }

        log.debug("Authentication Complete: " + authenticationComplete);

        return authenticationComplete;
    }


    private AccessGrant refreshAccessToken()
        throws GoogleDocsAuthenticationException,
            GoogleDocsRefreshTokenException,
            GoogleDocsServiceException
    {
        log.debug("Refreshing Access Token for " + AuthenticationUtil.getRunAsUser());
        OAuth2CredentialsInfo credentialInfo = oauth2CredentialsStoreService.getPersonalOAuth2Credentials(GoogleDocsConstants.REMOTE_SYSTEM);

        if (credentialInfo.getOAuthRefreshToken() != null)
        {

            AccessGrant accessGrant = null;
            try
            {

                accessGrant = connectionFactory.getOAuthOperations().refreshAccess(credentialInfo.getOAuthRefreshToken(), null, null);
            }
            catch (ApiException apie)
            {
                if (apie.getCause() instanceof ServiceException)
                {
                    ServiceException se = (ServiceException)apie.getCause();
                    if (se.getHttpErrorCodeOverride() == HttpStatus.SC_UNAUTHORIZED)
                    {
                        throw new GoogleDocsAuthenticationException("Token Refresh Failed.");
                    }
                }
            }
            catch (HttpClientErrorException hcee)
            {
                if (hcee.getStatusCode().value() == HttpStatus.SC_BAD_REQUEST)
                {
                    throw new GoogleDocsAuthenticationException(hcee.getMessage());
                }
                else
                {
                    throw new GoogleDocsServiceException(hcee.getMessage(), hcee.getStatusCode().ordinal());
                }

            }

            if (accessGrant != null)
            {
                Date expiresIn = null;

                if (accessGrant.getExpireTime() != null)
                {
                    if (accessGrant.getExpireTime() > 0L)
                    {
                        expiresIn = new Date(new Date().getTime() + accessGrant.getExpireTime());
                    }
                }

                try
                {
                    oauth2CredentialsStoreService.storePersonalOAuth2Credentials(GoogleDocsConstants.REMOTE_SYSTEM, accessGrant.getAccessToken(), credentialInfo.getOAuthRefreshToken(), expiresIn, new Date());
                }
                catch (NoSuchSystemException nsse)
                {
                    throw nsse;
                }
            }
            else
            {
                throw new GoogleDocsAuthenticationException("No Access Grant Returned.");
            }

            log.debug("Access Token Refreshed");
            return accessGrant;

        }
        else
        {
            throw new GoogleDocsRefreshTokenException("No Refresh Token Provided for " + AuthenticationUtil.getRunAsUser());
        }
    }


    private DocsService getDocsService(Connection<GoogleDocs> connection)
    {
        DocsService docsService = null;

        try
        {
            docsService = connection.getApi().setAuthentication(new DocsService(GoogleDocsConstants.APPLICATION_NAME));
        }
        catch (ApiException apie)
        {
            throw apie;
        }

        log.debug("Google Docs Client initiated");
        return docsService;

    }


    /**
     * @param type Document Type.  Must be document, spreadsheet or presentation
     * @param name Document File Name.  If null, name is generated based on document type. Ex type is document, file name is Untitled Document
     * @return Google Document List Entry
     * @throws GoogleDocsServiceException
     * @throws GoogleDocsTypeException
     * @throws GoogleDocsAuthenticationException
     * @throws GoogleDocsRefreshTokenException
     * @throws IOException
     */
    private DocumentListEntry createContent(String type, String name)
        throws GoogleDocsServiceException,
            GoogleDocsTypeException,
            GoogleDocsAuthenticationException,
            GoogleDocsRefreshTokenException,
            IOException
    {
        log.debug("Create Content");
        DocsService docsService = getDocsService(getConnection());

        DocumentListEntry entry = null;
        //Create Document List Entry based on type and get default name if null
        if (type.equals(GoogleDocsConstants.DOCUMENT_TYPE))
        {
            if (name == null)
            {
                name = GoogleDocsConstants.NEW_DOCUMENT_NAME;
            }
            entry = new DocumentEntry();
        }
        else if (type.equals(GoogleDocsConstants.SPREADSHEET_TYPE))
        {
            if (name == null)
            {
                name = GoogleDocsConstants.NEW_SPREADSHEET_NAME;
            }
            entry = new SpreadsheetEntry();
        }
        else if (type.equals(GoogleDocsConstants.PRESENTATION_TYPE))
        {
            if (name == null)
            {
                name = GoogleDocsConstants.NEW_PRESENTATION_NAME;
            }
            entry = new PresentationEntry();
        }

        if (entry != null)
        {
            //Set the title of entry
            entry.setTitle(new PlainTextConstruct(name));
            //Make the Document Hidden.  This does not work with Google Drive. See GOOGLEDOCS-91
            entry.setHidden(true);

            try
            {
                //Upload Content to Google
                return docsService.insert(new URL(GoogleDocsConstants.URL_BASE_FEED), entry);
            }
            catch (IOException ioe)
            {
                throw ioe;
            }
            catch (ServiceException se)
            {
                throw new GoogleDocsServiceException(se.getMessage(), se.getHttpErrorCodeOverride());
            }
        }
        else
        {
            throw new GoogleDocsTypeException("Type Unknown: " + type + ". Must be document, spreadsheet or presentation.");
        }
    }


    /* (non-Javadoc)
     * @see org.alfresco.integrations.google.docs.service.GoogleDocsService#createDocument(org.alfresco.service.cmr.repository.NodeRef)
     */
    public DocumentListEntry createDocument(NodeRef nodeRef)
        throws GoogleDocsServiceException,
            GoogleDocsTypeException,
            GoogleDocsAuthenticationException,
            GoogleDocsRefreshTokenException,
            IOException
    {
        log.debug("Create Google Document for node " + nodeRef);
        DocumentListEntry documentListEntry = createContent(GoogleDocsConstants.DOCUMENT_TYPE, fileFolderService.getFileInfo(nodeRef).getName());

        try
        {
            //Add temporary Node (with Content)
            ContentWriter writer = fileFolderService.getWriter(nodeRef);
            writer.setMimetype("application/vnd.openxmlformats-officedocument.wordprocessingml.document");
            writer.putContent(newDocument.getInputStream());

        }
        catch (IOException ioe)
        {
            throw ioe;
        }

        return documentListEntry;
    }


    /* (non-Javadoc)
     * @see org.alfresco.integrations.google.docs.service.GoogleDocsService#createSpreadSheet(org.alfresco.service.cmr.repository.NodeRef)
     */
    public DocumentListEntry createSpreadSheet(NodeRef nodeRef)
        throws GoogleDocsServiceException,
            GoogleDocsTypeException,
            GoogleDocsAuthenticationException,
            GoogleDocsRefreshTokenException,
            IOException
    {
        log.debug("Create Google Spreadsheet for node " + nodeRef);
        DocumentListEntry documentListEntry = createContent(GoogleDocsConstants.SPREADSHEET_TYPE, fileFolderService.getFileInfo(nodeRef).getName());

        try
        {
            //Add temporary Node (with Content)
            ContentWriter writer = fileFolderService.getWriter(nodeRef);
            writer.setMimetype("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet");
            writer.putContent(newSpreadsheet.getInputStream());

        }
        catch (IOException ioe)
        {
            throw ioe;
        }

        return documentListEntry;
    }


    /* (non-Javadoc)
     * @see org.alfresco.integrations.google.docs.service.GoogleDocsService#createPresentation(org.alfresco.service.cmr.repository.NodeRef)
     */
    public DocumentListEntry createPresentation(NodeRef nodeRef)
        throws GoogleDocsServiceException,
            GoogleDocsTypeException,
            GoogleDocsAuthenticationException,
            GoogleDocsRefreshTokenException,
            IOException
    {
        log.debug("Create Google Presentation for node " + nodeRef);
        DocumentListEntry documentListEntry = createContent(GoogleDocsConstants.PRESENTATION_TYPE, fileFolderService.getFileInfo(nodeRef).getName());

        try
        {
            //Add temporary Node (with Content)
            ContentWriter writer = fileFolderService.getWriter(nodeRef);
            writer.setMimetype("application/vnd.openxmlformats-officedocument.presentationml.presentation");
            writer.putContent(newPresentation.getInputStream());

        }
        catch (IOException ioe)
        {
            throw ioe;
        }

        return documentListEntry;
    }


    private void getDocument(NodeRef nodeRef, String resourceID)
        throws GoogleDocsAuthenticationException,
            GoogleDocsServiceException,
            GoogleDocsRefreshTokenException,
            IOException
    {
        log.debug("Get Google Document for node: " + nodeRef);
        DocsService docsService = getDocsService(getConnection());

        try
        {
            MediaContent mc = new MediaContent();

            String mimeType = null;
            String exportFormat = null;

            mimeType = validateMimeType(fileFolderService.getFileInfo(nodeRef).getContentData().getMimetype());
            log.debug("Current mimetype: " + fileFolderService.getFileInfo(nodeRef).getContentData().getMimetype()
                      + "; Mimetype of Google Doc: " + mimeType);
            exportFormat = getExportFormat(getContentType(nodeRef), mimeType);
            log.debug("Export format: " + exportFormat);

            mc.setUri(GoogleDocsConstants.URL_DOCUMENT_DOWNLOAD + "?docID=" + resourceID.substring(resourceID.lastIndexOf(':') + 1)
                      + "&exportFormat=" + exportFormat);

            log.debug("Export URL: " + mc.getUri());
            MediaSource ms = docsService.getMedia(mc);

            ContentWriter writer = fileFolderService.getWriter(nodeRef);
            writer.setMimetype(mimeType);
            writer.putContent(ms.getInputStream());

            DocumentListEntry documentListEntry = getDocumentListEntry(resourceID);

            renameNode(nodeRef, documentListEntry.getTitle().getPlainText());

            deleteContent(nodeRef, documentListEntry);

            postActivity(nodeRef);

            if (nodeService.hasAspect(nodeRef, ContentModel.ASPECT_TEMPORARY))
            {
                nodeService.removeAspect(nodeRef, ContentModel.ASPECT_TEMPORARY);
                log.debug("Temporary Aspect Removed");
            }
        }
        catch (IOException ioe)
        {
            throw ioe;
        }
        catch (ServiceException se)
        {
            throw new GoogleDocsServiceException(se.getMessage(), se.getHttpErrorCodeOverride());
        }
        catch (JSONException jsonException)
        {
            throw new GoogleDocsAuthenticationException("Unable to create activity entry: " + jsonException.getMessage(), jsonException);
        }
    }


    public void getDocument(NodeRef nodeRef)
        throws GoogleDocsAuthenticationException,
            GoogleDocsServiceException,
            GoogleDocsRefreshTokenException,
            IOException,
            ConstraintException
    {
        // TODO Wrap with try for null
        String resourceID = nodeService.getProperty(nodeRef, GoogleDocsModel.PROP_RESOURCE_ID).toString();

        getDocument(nodeRef, resourceID);
    }


    private void getSpreadSheet(NodeRef nodeRef, String resourceID)
        throws GoogleDocsAuthenticationException,
            GoogleDocsServiceException,
            GoogleDocsRefreshTokenException,
            IOException
    {
        log.debug("Get Google Spreadsheet for node: " + nodeRef);
        DocsService docsService = getDocsService(getConnection());

        try
        {
            MediaContent mc = new MediaContent();

            String mimeType = null;
            String exportFormat = null;

            mimeType = validateMimeType(fileFolderService.getFileInfo(nodeRef).getContentData().getMimetype());
            log.debug("Current mimetype: " + fileFolderService.getFileInfo(nodeRef).getContentData().getMimetype()
                      + "; Mimetype of Google Doc: " + mimeType);
            exportFormat = getExportFormat(getContentType(nodeRef), mimeType);
            log.debug("Export format: " + exportFormat);

            mc.setUri(GoogleDocsConstants.URL_SPREADSHEET_DOWNLOAD + "?key="
                      + resourceID.substring(resourceID.lastIndexOf(':') + 1) + "&exportFormat=" + exportFormat);

            log.debug("Export URL: " + mc.getUri());
            MediaSource ms = docsService.getMedia(mc);

            ContentWriter writer = fileFolderService.getWriter(nodeRef);
            writer.setMimetype(mimeType);
            writer.putContent(ms.getInputStream());

            DocumentListEntry documentListEntry = getDocumentListEntry(resourceID);

            renameNode(nodeRef, documentListEntry.getTitle().getPlainText());

            deleteContent(nodeRef, documentListEntry);

            postActivity(nodeRef);

            if (nodeService.hasAspect(nodeRef, ContentModel.ASPECT_TEMPORARY))
            {
                nodeService.removeAspect(nodeRef, ContentModel.ASPECT_TEMPORARY);
                log.debug("Temporary Aspect Removed");
            }
        }
        catch (IOException ioe)
        {
            throw ioe;
        }
        catch (ServiceException se)
        {
            throw new GoogleDocsServiceException(se.getMessage(), se.getHttpErrorCodeOverride());
        }
        catch (JSONException jsonException)
        {
            throw new GoogleDocsAuthenticationException("Unable to create activity entry: " + jsonException.getMessage(), jsonException);
        }
    }


    public void getSpreadSheet(NodeRef nodeRef)
        throws GoogleDocsAuthenticationException,
            GoogleDocsServiceException,
            GoogleDocsRefreshTokenException,
            IOException,
            ConstraintException
    {
        // TODO Wrap with try for null
        String resourceID = nodeService.getProperty(nodeRef, GoogleDocsModel.PROP_RESOURCE_ID).toString();

        getSpreadSheet(nodeRef, resourceID);
    }


    private void getPresentation(NodeRef nodeRef, String resourceID)
        throws GoogleDocsAuthenticationException,
            GoogleDocsServiceException,
            GoogleDocsRefreshTokenException,
            IOException
    {
        log.debug("Get Google Presentation for node: " + nodeRef);
        DocsService docsService = getDocsService(getConnection());

        try
        {
            MediaContent mc = new MediaContent();

            String mimeType = null;
            String exportFormat = null;

            mimeType = validateMimeType(fileFolderService.getFileInfo(nodeRef).getContentData().getMimetype());
            log.debug("Current mimetype: " + fileFolderService.getFileInfo(nodeRef).getContentData().getMimetype()
                      + "; Mimetype of Google Doc: " + mimeType);
            exportFormat = getExportFormat(getContentType(nodeRef), mimeType);
            log.debug("Export format: " + exportFormat);

            mc.setUri(GoogleDocsConstants.URL_PRESENTATION_DOWNLOAD + "?docID="
                      + resourceID.substring(resourceID.lastIndexOf(':') + 1) + "&exportFormat=" + exportFormat);

            log.debug("Export URL: " + mc.getUri());
            MediaSource ms = docsService.getMedia(mc);

            ContentWriter writer = fileFolderService.getWriter(nodeRef);
            writer.setMimetype(mimeType);
            writer.putContent(ms.getInputStream());

            DocumentListEntry documentListEntry = getDocumentListEntry(resourceID);

            renameNode(nodeRef, documentListEntry.getTitle().getPlainText());

            deleteContent(nodeRef, documentListEntry);

            postActivity(nodeRef);

            if (nodeService.hasAspect(nodeRef, ContentModel.ASPECT_TEMPORARY))
            {
                nodeService.removeAspect(nodeRef, ContentModel.ASPECT_TEMPORARY);
                log.debug("Temporary Aspect Removed");
            }
        }
        catch (IOException ioe)
        {
            throw ioe;
        }
        catch (ServiceException se)
        {
            throw new GoogleDocsServiceException(se.getMessage(), se.getHttpErrorCodeOverride());
        }
        catch (JSONException jsonException)
        {
            throw new GoogleDocsAuthenticationException("Unable to create activity entry: " + jsonException.getMessage(), jsonException);
        }
    }


    public void getPresentation(NodeRef nodeRef)
        throws GoogleDocsAuthenticationException,
            GoogleDocsServiceException,
            GoogleDocsRefreshTokenException,
            IOException,
            ConstraintException
    {
        // TODO Wrap with try for null
        String resourceID = nodeService.getProperty(nodeRef, GoogleDocsModel.PROP_RESOURCE_ID).toString();

        getPresentation(nodeRef, resourceID);
    }


    public DocumentListEntry uploadFile(NodeRef nodeRef)
        throws GoogleDocsAuthenticationException,
            GoogleDocsServiceException,
            GoogleDocsRefreshTokenException,
            IOException
    {
        log.debug("Upload " + nodeRef + " to Google");
        DocsService docsService = getDocsService(getConnection());

        DocumentListEntry uploaded = null;

        // It makes me want to cry that they don't support inputStreams.
        File file = null;

        try
        {
            // Get the read
            ContentReader reader = fileFolderService.getReader(nodeRef);
            file = File.createTempFile(nodeRef.getId(), ".tmp", TempFileProvider.getTempDir());
            reader.getContent(file);

            // Get the mimetype
            FileInfo fileInfo = fileFolderService.getFileInfo(nodeRef);
            String mimetype = fileInfo.getContentData().getMimetype();

            // Create MediFileSource
            MediaFileSource mediaFile = new MediaFileSource(file, mimetype);

            DocumentListEntry entry = new DocumentListEntry();
            entry.setTitle(new PlainTextConstruct(fileInfo.getName()));
            entry.setHidden(true);

            ResumableGDataFileUploader uploader = new ResumableGDataFileUploader.Builder(docsService, new URL(GoogleDocsConstants.URL_CREATE_MEDIA), mediaFile, entry).chunkSize(10485760L).build();
            uploader.start();
            log.debug("Start Upload");
            while (!uploader.isDone())
            {
                try
                {
                    Thread.sleep(100);
                    log.debug("Uploading...");
                }
                catch (InterruptedException ie)
                {
                    throw new GoogleDocsServiceException(ie.getMessage());
                }
            }

            if (uploader.getUploadState() != UploadState.CLIENT_ERROR)
            {
                uploaded = uploader.getResponse(DocumentListEntry.class);
                log.debug("Upload Complete");
            }
            else
            {
                throw new GoogleDocsServiceException("Error During Upload", HttpStatus.SC_METHOD_FAILURE);
            }

        }
        catch (IOException ioe)
        {
            throw ioe;
        }
        catch (ServiceException se)
        {
            throw new GoogleDocsServiceException(se.getMessage(), se.getHttpErrorCodeOverride());
        }
        finally
        {
            if (file != null)
            {
                file.delete();
            }
        }

        return uploaded;
    }


    public boolean deleteContent(NodeRef nodeRef, DocumentListEntry documentListEntry)
        throws GoogleDocsAuthenticationException,
            GoogleDocsServiceException,
            GoogleDocsRefreshTokenException,
            IOException
    {
        log.debug("Delete Google Doc for " + nodeRef);
        boolean deleted = false;

        DocsService docsService = getDocsService(getConnection());

        try
        {
            docsService.delete(new URL(GoogleDocsConstants.URL_BASE_FEED
                                       + "/"
                                       + documentListEntry.getResourceId().substring(documentListEntry.getResourceId().lastIndexOf(':') + 1)
                                       + "?delete=true"), documentListEntry.getEtag());

            unDecorateNode(nodeRef);
            deleted = true;
        }
        catch (IOException ioe)
        {
            throw ioe;
        }
        catch (ServiceException se)
        {
            throw new GoogleDocsServiceException(se.getMessage(), se.getHttpErrorCodeOverride());
        }

        log.debug("Deleted: " + deleted);
        return deleted;
    }


    public void decorateNode(NodeRef nodeRef, DocumentListEntry documentListEntry, boolean newcontent)
    {
        log.debug("Add Google Docs Aspect to " + nodeRef);
        behaviourFilter.disableBehaviour(nodeRef, ContentModel.ASPECT_VERSIONABLE);
        try
        {
            if (newcontent)
            {
                // Mark temporary until first save
                nodeService.addAspect(nodeRef, ContentModel.ASPECT_TEMPORARY, null);
                log.debug("Add Temporary Aspect");
            }

            // Get the googleMetadata to reference the Node
            Map<QName, Serializable> aspectProperties = new HashMap<QName, Serializable>();
            aspectProperties.put(GoogleDocsModel.PROP_RESOURCE_ID, documentListEntry.getResourceId());
            aspectProperties.put(GoogleDocsModel.PROP_EDITORURL, documentListEntry.getDocumentLink().getHref());
            nodeService.addAspect(nodeRef, GoogleDocsModel.ASPECT_EDITING_IN_GOOGLE, aspectProperties);
            log.debug("Resource Id: " + aspectProperties.get(GoogleDocsModel.PROP_RESOURCE_ID));
            log.debug("Editor Url:" + aspectProperties.get(GoogleDocsModel.PROP_EDITORURL));
        }
        finally
        {
            behaviourFilter.enableBehaviour(nodeRef, ContentModel.ASPECT_VERSIONABLE);
        }
    }


    public void unDecorateNode(NodeRef nodeRef)
    {
        log.debug("Remove Google Docs aspect from " + nodeRef);
        behaviourFilter.disableBehaviour(nodeRef, ContentModel.ASPECT_VERSIONABLE);
        try
        {
            if (nodeService.hasAspect(nodeRef, GoogleDocsModel.ASPECT_EDITING_IN_GOOGLE))
            {
                nodeService.removeAspect(nodeRef, GoogleDocsModel.ASPECT_EDITING_IN_GOOGLE);
            }
        }
        finally
        {
            behaviourFilter.enableBehaviour(nodeRef, ContentModel.ASPECT_VERSIONABLE);
        }
    }


    public void lockNode(NodeRef nodeRef)
    {
        log.debug("Lock Node " + nodeRef + " for Google Docs Editing");
        behaviourFilter.disableBehaviour(nodeRef, ContentModel.ASPECT_VERSIONABLE);
        try
        {
            nodeService.setProperty(nodeRef, GoogleDocsModel.PROP_LOCKED, true);
            lockservice.lock(nodeRef, LockType.READ_ONLY_LOCK);
        }
        finally
        {
            behaviourFilter.enableBehaviour(nodeRef, ContentModel.ASPECT_VERSIONABLE);
        }
    }


    public void unlockNode(NodeRef nodeRef)
    {
        log.debug("Unlock Node " + nodeRef + " from Google Docs Editing");
        behaviourFilter.disableBehaviour(nodeRef, ContentModel.ASPECT_VERSIONABLE);
        try
        {
            lockservice.unlock(nodeRef);
            nodeService.setProperty(nodeRef, GoogleDocsModel.PROP_LOCKED, false);
        }
        finally
        {
            behaviourFilter.enableBehaviour(nodeRef, ContentModel.ASPECT_VERSIONABLE);
        }
    }


    /**
     * Is the node locked by Googledocs? If the document is marked locked in the model, but not locked in the repository, the locked
     * property is set to false
     * 
     * @param nodeRef
     * @return
     */
    public boolean isLockedByGoogleDocs(NodeRef nodeRef)
    {

        boolean locked = false;

        if ((Boolean)nodeService.getProperty(nodeRef, GoogleDocsModel.PROP_LOCKED))
        {
            LockStatus lockStatus = lockservice.getLockStatus(nodeRef);
            if (lockStatus.equals(LockStatus.NO_LOCK))
            {
                // fix broken lock
                nodeService.setProperty(nodeRef, GoogleDocsModel.PROP_LOCKED, false);
            }
            else
            {
                locked = true;
            }
        }

        log.debug("Node " + nodeRef + " locked by Google Docs");

        return locked;
    }


    /**
     * @param nodeRef
     * @return Will return false is the document is not locked
     */
    public boolean isGoogleDocsLockOwner(NodeRef nodeRef)
    {
        boolean isOwner = false;

        if (isLockedByGoogleDocs(nodeRef))
        {
            LockStatus lockStatus = lockservice.getLockStatus(nodeRef);
            if (lockStatus.equals(LockStatus.LOCK_OWNER))
            {
                isOwner = true;
            }
        }

        return isOwner;
    }


    /**
     * Find nodes using duplicate name in same context (folder/space).
     * 
     * @param nodeRef
     * @param name if null, name will be pulled from nodeRef
     * @return
     */
    private NodeRef findLastDuplicate(NodeRef nodeRef, String name)
    {
        NodeRef lastDup = null;

        List<Pair<QName, Boolean>> sortProps = new ArrayList<Pair<QName, Boolean>>(1);
        sortProps.add(new Pair<QName, Boolean>(ContentModel.PROP_NAME, false));

        if (name == null)
        {
            name = fileFolderService.getFileInfo(nodeRef).getName();
        }

        PagingResults<FileInfo> results = fileFolderService.list(nodeService.getPrimaryParent(nodeRef).getParentRef(), true, false, addWildCardInName(name, fileFolderService.getFileInfo(nodeRef).getContentData().getMimetype()), null, sortProps, new PagingRequest(CannedQueryPageDetails.DEFAULT_PAGE_SIZE));

        List<FileInfo> page = results.getPage();
        FileInfo fileInfo = null;
        if (page.size() > 0)
        {
            fileInfo = page.get(0);
            lastDup = fileInfo.getNodeRef();

        }

        log.debug("NodeRef of most recent duplicate named file: " + (lastDup != null ? lastDup : " no duplicate named files"));
        return lastDup;
    }


    /**
     * Insert wildcard '*' into filename between name and extension
     * 
     * @param name
     * @param mimetype
     * @return
     */
    private String addWildCardInName(String name, String mimetype)
    {
        String extension = mimetypeService.getExtension(mimetype);
        return name.substring(0, name.length() - (extension.length() + 1)).concat("*." + extension);
    }


    /**
     * When the file format has changed or a new document is created we need to either change the extension or add an extension
     * 
     * @param name
     * @param office2007Pattern
     * @param office1997Pattern
     * @param office1997extension
     * @return
     */
    private String MSofficeExtensionHandler(String name, String office2007Pattern, String office1997Pattern,
            String office2007extension)
    {
        Pattern pattern = Pattern.compile(office1997Pattern);
        Matcher matcher = pattern.matcher(name);

        if (matcher.find())
        {
            name = name.substring(0, name.length() - 1);
        }
        else
        {
            Pattern _pattern = Pattern.compile(office2007Pattern);
            Matcher _matcher = _pattern.matcher(name);

            if (!_matcher.find())
            {
                name = name.concat(office2007extension);
            }
        }

        return name;
    }


    /**
     * Modify the file extension if the file mimetype has changed. If the name was changed while being edited in google docs update
     * the name in Alfresco. If the name is already in use in the current folder, append -{number} to the name or if it already has
     * a -{number} increment the number for the new file
     * 
     * @param nodeRef
     * @param name New name
     */
    private void renameNode(NodeRef nodeRef, String name)
        throws ConstraintException
    {
        // First, is the file name valid?
        ConstraintDefinition filenameConstraintDef = dictionaryService.getConstraint(QName.createQName(NamespaceService.CONTENT_MODEL_1_0_URI, "filename"));
        filenameConstraintDef.getConstraint().evaluate(name);

        // Not all file types can be round-tripped. This should correct
        // extensions on files where the format is modified or add an extension
        // to file types where there is no extension
        FileInfo fileInfo = fileFolderService.getFileInfo(nodeRef);
        String mimetype = fileInfo.getContentData().getMimetype();

        if (mimetype.equals("application/vnd.openxmlformats-officedocument.wordprocessingml.document"))
        {
            name = MSofficeExtensionHandler(name, "\\.docx$", "\\.doc$", ".docx");
        }
        else if (mimetype.equals("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"))
        {
            name = MSofficeExtensionHandler(name, "\\.xlsx$", "\\.xls$", ".xlsx");
        }
        else if (mimetype.equals("application/vnd.openxmlformats-officedocument.presentationml.presentation"))
        {
            name = MSofficeExtensionHandler(name, "\\.pptx$", "\\.ppt$", ".pptx");
        }
        else if (mimetype.equals("application/vnd.oasis.opendocument.text"))
        {
            Pattern odt_pattern = Pattern.compile("\\.odt$");
            Matcher odt_matcher = odt_pattern.matcher(name);

            if (!odt_matcher.find())
            {
                Pattern sxw_pattern = Pattern.compile("\\.sxw$");
                Matcher sxw_matcher = sxw_pattern.matcher(name);

                if (sxw_matcher.find())
                {
                    name = name.substring(0, name.length() - 4);
                    name = name.concat(".odt");
                }
            }
        }

        //Get the last known node with the same name (+number) in the same folder
        NodeRef lastDup = findLastDuplicate(nodeRef, name);

        if (lastDup != null)
        {
            //if it is not the same file increment (or add number to) the filename
            if (!lastDup.equals(fileInfo.getNodeRef()))
            {
                name = filenameUtil.incrementFileName(fileFolderService.getFileInfo(lastDup).getName(), fileInfo.getContentData().getMimetype());
            }
        }

        // If there is no change in the name we don't want to make a change in
        // the repo
        if (!fileInfo.getName().equals(name))
        {
            nodeService.setProperty(nodeRef, ContentModel.PROP_NAME, name);
        }
    }


    public boolean hasConcurrentEditors(NodeRef nodeRef)
        throws GoogleDocsAuthenticationException,
            GoogleDocsRefreshTokenException,
            GoogleDocsServiceException,
            IOException
    {
        log.debug("Check for Concurrent Editors (Edits that have occured in the last " + idleThreshold + " seconds)");
        DocsService docsService = getDocsService(getConnection());

        String resourceID = nodeService.getProperty(nodeRef, GoogleDocsModel.PROP_RESOURCE_ID).toString();

        boolean concurrentChange = false;

        try
        {
            RevisionFeed revisionFeed = docsService.getFeed(new URL(GoogleDocsConstants.URL_BASE_FEED + "/"
                                                                    + resourceID.substring(resourceID.lastIndexOf(':') + 1)
                                                                    + "/revisions"), RevisionFeed.class);

            List<RevisionEntry> revisionList = revisionFeed.getEntries();

            if (revisionList.size() > 1)
            {
                log.debug("Revisions Found");
                Collections.sort(revisionList, new RevisionEntryComparator());

                // Find any revisions occuring within the last 'idleThreshold'
                // seconds
                List<RevisionEntry> workingList = new ArrayList<RevisionEntry>();

                Calendar bufferTime = Calendar.getInstance();
                bufferTime.add(Calendar.SECOND, -idleThreshold);

                for (RevisionEntry entry : revisionList)
                {
                    if (new Date(entry.getUpdated().getValue()).after(new Date(bufferTime.getTimeInMillis())))
                    {
                        workingList.add(entry);
                    }
                    else
                    {
                        // once we past 'idleThreshold' seconds get out of here
                        break;
                    }
                }

                // If there any revisions that occurred within the last
                // 'idleThreshold' seconds of time....
                if (workingList.size() > 0)
                {
                    log.debug("Revisions within threshhold found");
                    // Filter the current user from the list
                    for (int i = workingList.size() - 1; i >= 0; i--)
                    {
                        RevisionEntry revisionEntry = workingList.get(i);
                        String email = getUserMetadata().getAuthors().get(0).getEmail();

                        // if there is no author -- the entry is the initial
                        // creation
                        if (revisionEntry.getAuthors().size() > 0)
                        {
                            if (revisionEntry.getAuthors().get(0).getEmail().equals(email))
                            {
                                workingList.remove(i);
                            }
                        }
                        else
                        {
                            workingList.remove(i);
                        }
                    }
                }

                // Are there are changes by other users within the last
                // 'idleThreshold' seconds
                if (workingList.size() > 0)
                {
                    log.debug("Revisions not made by current user found.");
                    concurrentChange = true;
                }

            }
            else
            {
                String email = getUserMetadata().getAuthors().get(0).getEmail();

                // if the authors list is empty -- the author was the original
                // creator and it is the initial copy
                if (!revisionList.get(0).getAuthors().isEmpty())
                {

                    if (!revisionList.get(0).getAuthors().get(0).getEmail().equals(email))
                    {
                        Calendar bufferTime = Calendar.getInstance();
                        bufferTime.add(Calendar.SECOND, -idleThreshold);

                        if (new Date(revisionList.get(0).getUpdated().getValue()).before(new Date(bufferTime.getTimeInMillis())))
                        {
                            log.debug("Revisions not made by current user found.");
                            concurrentChange = true;
                        }
                    }
                }
            }

        }
        catch (IOException ioe)
        {
            throw ioe;
        }
        catch (ServiceException se)
        {
            throw new GoogleDocsServiceException(se.getMessage(), se.getHttpErrorCodeOverride());
        }

        log.debug("Concurrent Edits: " + concurrentChange);
        return concurrentChange;
    }


    public DocumentListEntry getDocumentListEntry(String resourceID)
        throws IOException,
            GoogleDocsServiceException,
            GoogleDocsAuthenticationException,
            GoogleDocsRefreshTokenException
    {
        log.debug("Get Document list entry for resource " + resourceID);
        DocsService docsService = getDocsService(getConnection());

        DocumentListEntry documentListEntry = null;

        try
        {
            documentListEntry = docsService.getEntry(new URL(GoogleDocsConstants.URL_BASE_FEED + "/"
                                                             + resourceID.substring(resourceID.lastIndexOf(':') + 1)), DocumentListEntry.class);
        }
        catch (IOException ioe)
        {
            throw ioe;
        }
        catch (ServiceException se)
        {
            throw new GoogleDocsServiceException(se.getMessage(), se.getHttpErrorCodeOverride());
        }
        return documentListEntry;
    }


    public MetadataEntry getUserMetadata()
        throws GoogleDocsAuthenticationException,
            GoogleDocsRefreshTokenException,
            GoogleDocsServiceException,
            IOException
    {
        log.debug("Get Google Docs user metadata");
        DocsService docsService = getDocsService(getConnection());

        MetadataEntry metadataEntry = null;

        try
        {
            metadataEntry = docsService.getEntry(new URL(GoogleDocsConstants.METADATA_URL), MetadataEntry.class);
        }
        catch (IOException ioe)
        {
            throw ioe;
        }
        catch (ServiceException se)
        {
            throw new GoogleDocsServiceException(se.getMessage(), se.getHttpErrorCodeOverride());
        }

        return metadataEntry;
    }


    private void postActivity(NodeRef nodeRef)
        throws JSONException
    {
        log.debug("Create Activity Stream Entry");
        if (personService.personExists(AuthenticationUtil.getRunAsUser()))
        {
            try
            {
                String activityType = FILE_UPDATED;
                if (nodeService.hasAspect(nodeRef, ContentModel.ASPECT_TEMPORARY))
                {
                    activityType = FILE_ADDED;
                }

                String siteId = siteService.getSite(nodeRef).getShortName();

                JSONObject jsonActivityData = new JSONObject();

                // Using local getPerson ... not cloud
                // personservice.getPerson(nodeRef) which returns personInfo object
                PersonInfo personInfo = getPerson(personService.getPerson(AuthenticationUtil.getRunAsUser(), false));

                jsonActivityData.put("firstName", personInfo.getFirstName());
                jsonActivityData.put("lastName", personInfo.getLastName());
                jsonActivityData.put("title", fileFolderService.getFileInfo(nodeRef).getName());
                jsonActivityData.put("page", "document-details?nodeRef=" + nodeRef.toString());
                jsonActivityData.put("nodeRef", nodeRef.toString());

                if (AuthenticationUtil.isMtEnabled())
                {
                    // MT share - add tenantDomain
                    jsonActivityData.put("tenantDomain", tenantService.getCurrentUserDomain());
                }

                activityService.postActivity(activityType, siteId, GoogleDocsService.class.getSimpleName(), jsonActivityData.toString());
                log.debug("Post Activity Stream Entry -- type:" + activityType + "; site: " + siteId + "; Data: "
                          + jsonActivityData);
            }
            catch (JSONException jsonException)
            {
                throw jsonException;
            }
        } else {
            log.debug("Activity stream entry not created -- user does not exist.");
        }
    }


    private PersonInfo getPerson(NodeRef nodeRef)
    {
        return new PersonInfo(nodeRef, AuthenticationUtil.getRunAsUser(), nodeService.getProperty(nodeRef, ContentModel.PROP_FIRSTNAME).toString(), nodeService.getProperty(nodeRef, ContentModel.PROP_LASTNAME).toString());
    }


}
