/**
 * Copyright (C) 2005-2013 Alfresco Software Limited.
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


import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
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
import org.alfresco.integrations.google.docs.exceptions.NotInGoogleDriveException;
import org.alfresco.integrations.google.docs.utils.FileNameUtil;
import org.alfresco.integrations.google.docs.utils.FileRevisionComparator;
import org.alfresco.model.ContentModel;
import org.alfresco.query.CannedQueryPageDetails;
import org.alfresco.query.PagingRequest;
import org.alfresco.query.PagingResults;
import org.alfresco.repo.content.MimetypeMap;
import org.alfresco.repo.policy.BehaviourFilter;
import org.alfresco.repo.security.authentication.AuthenticationUtil;
import org.alfresco.repo.site.SiteServiceImpl;
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
import org.alfresco.service.cmr.security.AuthorityService;
import org.alfresco.service.cmr.security.PersonService;
import org.alfresco.service.cmr.security.PersonService.PersonInfo;
import org.alfresco.service.cmr.site.SiteInfo;
import org.alfresco.service.cmr.site.SiteService;
import org.alfresco.service.namespace.NamespaceService;
import org.alfresco.service.namespace.QName;
import org.alfresco.util.Pair;
import org.alfresco.util.TempFileProvider;
import org.apache.commons.httpclient.HttpStatus;
import org.apache.commons.lang.StringUtils;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.json.JSONException;
import org.json.JSONObject;
import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.Resource;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;
import org.springframework.social.connect.Connection;
import org.springframework.social.google.api.Google;
import org.springframework.social.google.api.drive.AdditionalRole;
import org.springframework.social.google.api.drive.DriveFile;
import org.springframework.social.google.api.drive.DriveFilesPage;
import org.springframework.social.google.api.drive.DriveOperations;
import org.springframework.social.google.api.drive.FileRevision;
import org.springframework.social.google.api.drive.PermissionRole;
import org.springframework.social.google.api.drive.PermissionType;
import org.springframework.social.google.api.drive.UploadParameters;
import org.springframework.social.google.api.drive.UserPermission;
import org.springframework.social.google.api.userinfo.GoogleUserProfile;
import org.springframework.social.google.api.userinfo.UserInfoOperations;
import org.springframework.social.google.connect.GoogleConnectionFactory;
import org.springframework.social.oauth2.AccessGrant;
import org.springframework.social.oauth2.GrantType;
import org.springframework.social.oauth2.OAuth2Parameters;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.HttpServerErrorException;
import org.springframework.web.client.HttpStatusCodeException;
import org.springframework.web.client.RestTemplate;

import com.google.gdata.client.docs.DocsService;
import com.google.gdata.data.acl.AclEntry;
import com.google.gdata.data.acl.AclFeed;
import com.google.gdata.data.acl.AclRole;
import com.google.gdata.data.acl.AclScope.Type;
import com.google.gdata.data.docs.DocumentListEntry;
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
    private static final Log                 log                       = LogFactory.getLog(GoogleDocsServiceImpl.class);

    // Services
    private OAuth2CredentialsStoreService    oauth2CredentialsStoreService;
    private GoogleConnectionFactory          connectionFactory;
    private FileFolderService                fileFolderService;
    private NodeService                      nodeService;
    private LockService                      lockservice;
    private MimetypeService                  mimetypeService;
    private BehaviourFilter                  behaviourFilter;
    private ActivityService                  activityService;
    private SiteService                      siteService;
    private TenantService                    tenantService;
    private PersonService                    personService;
    private AuthorityService                 authorityService;

    private DictionaryService                dictionaryService;
    private FileNameUtil                     filenameUtil;

    // Property Mappings
    private Map<String, String>              importFormats             = new HashMap<String, String>();
    private Map<String, Map<String, String>> exportFormats             = new HashMap<String, Map<String, String>>();
    private Map<String, String>              upgradeMappings           = new HashMap<String, String>();
    private Map<String, String>              downgradeMappings         = new HashMap<String, String>();

    // New Content
    private Resource                         newDocument;
    private Resource                         newSpreadsheet;
    private Resource                         newPresentation;

    // Time (in seconds) between last edit and now to consider edits as
    // concurrent
    private int                              idleThreshold             = 0;

    private boolean                          enabled           = true;

    // Activities
    private static final String              FILE_ADDED                = "org.alfresco.documentlibrary.file-added";
    private static final String              FILE_UPDATED              = "org.alfresco.documentlibrary.file-updated";

    // Permission roles
    private static final String              PERMISSION_ROLE_READER    = "reader";
    private static final String              PERMISSION_ROLE_WRITER    = "writer";
    private static final String              PERMISSION_ROLE_OWNER     = "owner";
    private static final String              PERMISSION_ROLE_COMMENTER = "commenter";

    // Permission authority types
    private static final String              PERMISSION_TYPE_USER      = "user";
    private static final String              PERMISSION_TYPE_GROUP     = "group";
    private static final String              PERMISSION_TYPE_DOMAIN    = "domain";
    private static final String              PERMISSION_TYPE_ANYONE    = "anyone";


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


    public void setConnectionFactory(GoogleConnectionFactory connectionFactory)
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


    public void setAuthorityService(AuthorityService authorityService)
    {
        this.authorityService = authorityService;
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


    /**
     * Can the mimetype be imported from Google Docs to Alfresco?
     * 
     * @param mimetype
     * @return boolean
     */
    public boolean isImportable(String mimetype)
    {
        return importFormats.containsKey(mimetype);
    }


    /**
     * Get the Google document type (Document, Spreadsheet, Presentation)
     * 
     * @param mimetype
     * @return String
     */
    private String getImportType(String mimetype)
    {
        return importFormats.get(mimetype);
    }


    /**
     * @param mimetype
     * @return
     * @throws
     */
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


    /**
     * Get a Set of all the mimetypes that can be exported for the Google Document type
     * 
     * @param type
     * @return Set
     */
    private Set<String> getExportableMimeTypes(String type)
    {
        Set<String> mimetypes = new HashSet<String>();

        if (exportFormats.containsKey(type))
        {
            mimetypes = exportFormats.get(type).keySet();
        }

        return mimetypes;
    }


    /**
     * Will the mimetype be upgraded if exported to Google Docs?
     * 
     * @param mimetype
     * @return
     */
    private boolean isUpgrade(String mimetype)
    {
        return upgradeMappings.containsKey(mimetype);
    }


    /**
     * Will the mimetype be downgraded if exported to Google Docs?
     * 
     * @param mimetype
     * @return
     */
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


    /**
     * @param mimeType Mimetype of the Node
     * @return If the Document must be returned as a different type, returns the new type
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


    /**
     * Get a connection to the Google APIs. Will attempt to refresh tokens if they are invalid. If unable to refresh return a
     * GoogleDocsRefreshTokenException.
     * 
     * @return
     * @throws GoogleDocsAuthenticationException
     * @throws GoogleDocsRefreshTokenException
     * @throws GoogleDocsServiceException
     */
    private Connection<Google> getConnection()
        throws GoogleDocsAuthenticationException,
            GoogleDocsRefreshTokenException,
            GoogleDocsServiceException
    {
        Connection<Google> connection = null;

        // OAuth credentials for the current user, if the exist
        OAuth2CredentialsInfo credentialInfo = oauth2CredentialsStoreService.getPersonalOAuth2Credentials(GoogleDocsConstants.REMOTE_SYSTEM);

        if (credentialInfo != null)
        {
            log.debug("OAuth Access Token Exists: " + credentialInfo.getOAuthAccessToken());
            AccessGrant accessGrant = new AccessGrant(credentialInfo.getOAuthAccessToken());

            try
            {
                log.debug("Attempt to create OAuth Connection");
                connection = connectionFactory.createConnection(accessGrant);
            }
            catch (HttpClientErrorException hcee)
            {
                log.debug(hcee.getResponseBodyAsString());
                if (hcee.getStatusCode().value() == HttpStatus.SC_UNAUTHORIZED)
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
                else
                {
                    throw new GoogleDocsServiceException(hcee.getMessage(), hcee, hcee.getStatusCode().value());
                }
            }
            catch (HttpServerErrorException hsee)
            {
                throw new GoogleDocsServiceException(hsee.getMessage(), hsee, hsee.getStatusCode().value());
            }
        }

        log.debug("Connection Created");
        return connection;
    }


    /**
     * Has the current user authenticated to Google Drive?
     * 
     * @return
     */
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


    /**
     * The oauth authentication url
     * 
     * @param state the value of the oauth state parameter to be passed in the authentication url
     * @return The complete oauth authentication url
     */
    public String getAuthenticateUrl(String state)
    {
        String authenticateUrl = null;

        if (state != null)
        {
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

            oauth2CredentialsStoreService.storePersonalOAuth2Credentials(GoogleDocsConstants.REMOTE_SYSTEM, accessGrant.getAccessToken(), accessGrant.getRefreshToken(), new Date(accessGrant.getExpireTime()), new Date());

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

                accessGrant = connectionFactory.getOAuthOperations().refreshAccess(credentialInfo.getOAuthRefreshToken(), GoogleDocsConstants.SCOPE, null);
            }
            catch (HttpClientErrorException hcee)
            {
                if (hcee.getStatusCode().value() == HttpStatus.SC_BAD_REQUEST)
                {
                    throw new GoogleDocsAuthenticationException(hcee.getMessage());
                }
                else if (hcee.getStatusCode().value() == HttpStatus.SC_UNAUTHORIZED)
                {
                    throw new GoogleDocsAuthenticationException("Token Refresh Failed.");
                }
                else
                {
                    throw new GoogleDocsServiceException(hcee.getMessage(), hcee.getStatusCode().value());
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


    private DriveOperations getDriveOperations(Connection<Google> connection)
    {
        log.debug("Initiating Google Drive Client");
        return connection.getApi().driveOperations();
    }


    /*
     * (non-Javadoc)
     * 
     * @see
     * org.alfresco.integrations.google.docs.service.GoogleDocsService#createDocument(org.alfresco.service.cmr.repository.NodeRef)
     */
    public DriveFile createDocument(NodeRef nodeRef)
        throws GoogleDocsServiceException,
            GoogleDocsTypeException,
            GoogleDocsAuthenticationException,
            GoogleDocsRefreshTokenException,
            IOException
    {
        DriveOperations driveOperations = getDriveOperations(getConnection());
        log.debug("Create Google Document for node " + nodeRef);

        DriveFile driveFile = null;
        String name = fileFolderService.getFileInfo(nodeRef).getName();
        // To be editable a new document must use the Google Document mimetype.
        String mimetype = GoogleDocsConstants.DOCUMENT_MIMETYPE;

        // If the node does not have a name, set a default for the type
        if (name == null)
        {
            name = GoogleDocsConstants.NEW_DOCUMENT_NAME;
        }

        try
        {
            // Create the working Directory
            DriveFile workingDir = createWorkingDirectory(nodeRef);

            // Create the Google Document in the working directory
            driveFile = new DriveFile.Builder().setParents(workingDir.getId()).setTitle(name).setHidden(true).setMimeType(mimetype).build();
            driveFile = driveOperations.createFileMetadata(driveFile);

            // Add temporary Node (with Content) to repository.
            ContentWriter writer = fileFolderService.getWriter(nodeRef);
            writer.setMimetype(MimetypeMap.MIMETYPE_OPENXML_WORDPROCESSING);
            writer.putContent(newDocument.getInputStream());

        }
        catch (IOException ioe)
        {
            throw ioe;
        }

        return driveFile;
    }


    /*
     * (non-Javadoc)
     * 
     * @see
     * org.alfresco.integrations.google.docs.service.GoogleDocsService#createSpreadSheet(org.alfresco.service.cmr.repository.NodeRef
     * )
     */
    public DriveFile createSpreadSheet(NodeRef nodeRef)
        throws GoogleDocsServiceException,
            GoogleDocsTypeException,
            GoogleDocsAuthenticationException,
            GoogleDocsRefreshTokenException,
            IOException
    {
        DriveOperations driveOperations = getDriveOperations(getConnection());
        log.debug("Create Google Spreadsheet for node " + nodeRef);

        DriveFile driveFile = null;
        String name = fileFolderService.getFileInfo(nodeRef).getName();
        // To be editable, a new spreadsheet must use the Google Spreadsheet mimetype.
        String mimetype = GoogleDocsConstants.SPREADSHEET_MIMETYPE;

        // If the node does not have a name, set a default for the type
        if (name == null)
        {
            name = GoogleDocsConstants.NEW_SPREADSHEET_NAME;
        }

        try
        {
            // Create the working Directory
            DriveFile workingDir = createWorkingDirectory(nodeRef);

            // Create the Google Spreadsheet in the working directory
            driveFile = new DriveFile.Builder().setParents(workingDir.getId()).setTitle(name).setHidden(true).setMimeType(mimetype).build();
            driveFile = driveOperations.createFileMetadata(driveFile);

            // Add temporary Node (with Content) to the repository
            ContentWriter writer = fileFolderService.getWriter(nodeRef);
            writer.setMimetype(MimetypeMap.MIMETYPE_OPENXML_SPREADSHEET);
            writer.putContent(newSpreadsheet.getInputStream());

        }
        catch (IOException ioe)
        {
            throw ioe;
        }

        return driveFile;
    }


    /*
     * (non-Javadoc)
     * 
     * @see
     * org.alfresco.integrations.google.docs.service.GoogleDocsService#createPresentation(org.alfresco.service.cmr.repository.NodeRef
     * )
     */
    public DriveFile createPresentation(NodeRef nodeRef)
        throws GoogleDocsServiceException,
            GoogleDocsTypeException,
            GoogleDocsAuthenticationException,
            GoogleDocsRefreshTokenException,
            IOException
    {
        DriveOperations driveOperations = getDriveOperations(getConnection());
        log.debug("Create Google Presentation for node " + nodeRef);

        DriveFile driveFile = null;
        String name = fileFolderService.getFileInfo(nodeRef).getName();
        // To be editable a new presentation must use the Google Presentation mimetype
        String mimetype = GoogleDocsConstants.PRESENTATION_MIMETYPE;

        // If the node does not have a name, set a default for the type
        if (name == null)
        {
            name = GoogleDocsConstants.NEW_PRESENTATION_NAME;
        }

        try
        {
            // Create the working Directory
            DriveFile workingDir = createWorkingDirectory(nodeRef);

            // Create the Google Document in the working directory
            driveFile = new DriveFile.Builder().setParents(workingDir.getId()).setTitle(name).setHidden(true).setMimeType(mimetype).build();
            driveFile = driveOperations.createFileMetadata(driveFile);

            // Add temporary Node (with Content) to repository
            ContentWriter writer = fileFolderService.getWriter(nodeRef);
            writer.setMimetype(MimetypeMap.MIMETYPE_OPENXML_PRESENTATION);
            writer.putContent(newPresentation.getInputStream());

        }
        catch (IOException ioe)
        {
            throw ioe;
        }

        return driveFile;
    }


    /**
     * Get the Document from the users Google Drive account. The Document and its working directory will be removed from their
     * Google Drive account. The editingInGoogle aspect will be removed.
     * 
     * @param nodeRef
     * @param resourceID
     * @throws GoogleDocsAuthenticationException
     * @throws GoogleDocsServiceException
     * @throws GoogleDocsRefreshTokenException
     */
    private void getDocument(NodeRef nodeRef, String resourceID, boolean removeFromDrive)
        throws GoogleDocsAuthenticationException,
            GoogleDocsServiceException,
            IOException,
            GoogleDocsRefreshTokenException
    {
        log.debug("Get Google Document for node: " + nodeRef);
        DriveOperations driveOperations = getDriveOperations(getConnection());

        try
        {
            String mimetype = null;

            mimetype = validateMimeType(fileFolderService.getFileInfo(nodeRef).getContentData().getMimetype());
            log.debug("Current mimetype: " + fileFolderService.getFileInfo(nodeRef).getContentData().getMimetype()
                      + "; Mimetype of Google Doc: " + mimetype);
            log.debug("Export format: " + mimetype);

            DriveFile driveFile = driveOperations.getFile(resourceID.substring(resourceID.lastIndexOf(':') + 1));

            InputStream inputstream = getFileInputStream(driveFile, mimetype);

            ContentWriter writer = fileFolderService.getWriter(nodeRef);
            writer.setMimetype(mimetype);
            writer.putContent(inputstream);

            renameNode(nodeRef, driveFile.getTitle());

            saveSharedInfo(nodeRef, resourceID);

            if (removeFromDrive)
            {
                deleteContent(nodeRef, driveFile);
            }
            else
            {
                nodeService.setProperty(nodeRef, GoogleDocsModel.PROP_REVISION_ID, getLatestRevision(driveFile).getId());
            }

            postActivity(nodeRef);

            if (nodeService.hasAspect(nodeRef, ContentModel.ASPECT_TEMPORARY) && removeFromDrive)
            {
                nodeService.removeAspect(nodeRef, ContentModel.ASPECT_TEMPORARY);
                log.debug("Temporary Aspect Removed");
            }
        }
        catch (HttpStatusCodeException hsce)
        {
            throw new GoogleDocsServiceException(hsce.getMessage(), hsce.getStatusCode().value());
        }
        catch (JSONException jsonException)
        {
            throw new GoogleDocsAuthenticationException("Unable to create activity entry: " + jsonException.getMessage(), jsonException);
        }
    }


    /*
     * (non-Javadoc)
     * 
     * @see org.alfresco.integrations.google.docs.service.GoogleDocsService#getDocument(org.alfresco.service.cmr.repository.NodeRef)
     */
    public void getDocument(NodeRef nodeRef, boolean removeFromDrive)
        throws GoogleDocsAuthenticationException,
            GoogleDocsServiceException,
            IOException,
            GoogleDocsRefreshTokenException
    {
        String resourceID = nodeService.getProperty(nodeRef, GoogleDocsModel.PROP_RESOURCE_ID).toString();
        if (resourceID == null)
        {
           throw new NotInGoogleDriveException(nodeRef);
        }

        getDocument(nodeRef, resourceID, removeFromDrive);
    }


    public void getDocument(NodeRef nodeRef)
        throws GoogleDocsAuthenticationException,
            GoogleDocsServiceException,
            IOException,
            GoogleDocsRefreshTokenException
    {
        String resourceID = nodeService.getProperty(nodeRef, GoogleDocsModel.PROP_RESOURCE_ID).toString();
        if (resourceID == null)
        {
           throw new NotInGoogleDriveException(nodeRef);
        }

        getDocument(nodeRef, resourceID, true);
    }


    /**
     * Get the Document from the users Google Drive account. The Document and its working directory will be removed from their
     * Google Drive account. The editingInGoogle aspect will be removed.
     * 
     * @param nodeRef
     * @param resourceID
     * @throws GoogleDocsAuthenticationException
     * @throws GoogleDocsServiceException
     * @throws GoogleDocsRefreshTokenException
     */
    private void getSpreadSheet(NodeRef nodeRef, String resourceID, boolean removeFromDrive)
        throws GoogleDocsAuthenticationException,
            GoogleDocsServiceException,
            GoogleDocsRefreshTokenException,
            IOException
    {
        log.debug("Get Google Spreadsheet for node: " + nodeRef);
        DriveOperations driveOperations = getDriveOperations(getConnection());

        try
        {
            String mimetype = null;

            mimetype = validateMimeType(fileFolderService.getFileInfo(nodeRef).getContentData().getMimetype());
            log.debug("Current mimetype: " + fileFolderService.getFileInfo(nodeRef).getContentData().getMimetype()
                      + "; Mimetype of Google Doc: " + mimetype);
            log.debug("Export format: " + mimetype);

            DriveFile driveFile = driveOperations.getFile(resourceID.substring(resourceID.lastIndexOf(':') + 1));

            InputStream inputStream = getFileInputStream(driveFile, mimetype);

            ContentWriter writer = fileFolderService.getWriter(nodeRef);
            writer.setMimetype(mimetype);
            writer.putContent(inputStream);

            renameNode(nodeRef, driveFile.getTitle());

            saveSharedInfo(nodeRef, resourceID);

            if (removeFromDrive)
            {
                deleteContent(nodeRef, driveFile);
            }
            else
            {
                nodeService.setProperty(nodeRef, GoogleDocsModel.PROP_REVISION_ID, getLatestRevision(driveFile).getId());
            }

            postActivity(nodeRef);

            if (nodeService.hasAspect(nodeRef, ContentModel.ASPECT_TEMPORARY) && removeFromDrive)
            {
                nodeService.removeAspect(nodeRef, ContentModel.ASPECT_TEMPORARY);
                log.debug("Temporary Aspect Removed");
            }
        }
        catch (IOException ioe)
        {
            throw ioe;
        }
        catch (HttpStatusCodeException hsce)
        {
            throw new GoogleDocsServiceException(hsce.getMessage(), hsce.getStatusCode().value());
        }
        catch (JSONException jsonException)
        {
            throw new GoogleDocsAuthenticationException("Unable to create activity entry: " + jsonException.getMessage(), jsonException);
        }
    }


    public void getSpreadSheet(NodeRef nodeRef, boolean removeFromDrive)
        throws GoogleDocsAuthenticationException,
            GoogleDocsServiceException,
            IOException,
            GoogleDocsRefreshTokenException
    {
        String resourceID = nodeService.getProperty(nodeRef, GoogleDocsModel.PROP_RESOURCE_ID).toString();
        if (resourceID == null)
        {
           throw new NotInGoogleDriveException(nodeRef);
        }

        getSpreadSheet(nodeRef, resourceID, removeFromDrive);
    }


    /*
     * (non-Javadoc)
     * 
     * @see
     * org.alfresco.integrations.google.docs.service.GoogleDocsService#getSpreadSheet(org.alfresco.service.cmr.repository.NodeRef)
     */
    public void getSpreadSheet(NodeRef nodeRef)
        throws GoogleDocsAuthenticationException,
            GoogleDocsServiceException,
            IOException,
            GoogleDocsRefreshTokenException
    {
        String resourceID = nodeService.getProperty(nodeRef, GoogleDocsModel.PROP_RESOURCE_ID).toString();
        if (resourceID == null)
        {
           throw new NotInGoogleDriveException(nodeRef);
        }

        getSpreadSheet(nodeRef, resourceID, true);
    }


    /**
     * Get the Document from the users Google Drive account. The Document and its working directory will be removed from their
     * Google Drive account. The editingInGoogle aspect will be removed.
     * 
     * @param nodeRef
     * @param resourceID
     * @throws GoogleDocsAuthenticationException
     * @throws GoogleDocsServiceException
     * @throws GoogleDocsRefreshTokenException
     */
    private void getPresentation(NodeRef nodeRef, String resourceID, boolean removeFromDrive)
        throws GoogleDocsAuthenticationException,
            GoogleDocsServiceException,
            GoogleDocsRefreshTokenException,
            IOException
    {
        log.debug("Get Google Presentation for node: " + nodeRef);
        DriveOperations driveOperations = getDriveOperations(getConnection());

        try
        {
            String mimetype = null;

            mimetype = validateMimeType(fileFolderService.getFileInfo(nodeRef).getContentData().getMimetype());
            log.debug("Current mimetype: " + fileFolderService.getFileInfo(nodeRef).getContentData().getMimetype()
                      + "; Mimetype of Google Doc: " + mimetype);
            log.debug("Export format: " + mimetype);

            DriveFile driveFile = driveOperations.getFile(resourceID.substring(resourceID.lastIndexOf(':') + 1));

            InputStream inputStream = getFileInputStream(driveFile, mimetype);

            ContentWriter writer = fileFolderService.getWriter(nodeRef);
            writer.setMimetype(mimetype);
            writer.putContent(inputStream);

            renameNode(nodeRef, driveFile.getTitle());

            saveSharedInfo(nodeRef, resourceID);

            if (removeFromDrive)
            {
                deleteContent(nodeRef, driveFile);
            }
            else
            {
                nodeService.setProperty(nodeRef, GoogleDocsModel.PROP_REVISION_ID, getLatestRevision(driveFile).getId());
            }

            postActivity(nodeRef);

            if (nodeService.hasAspect(nodeRef, ContentModel.ASPECT_TEMPORARY) && removeFromDrive)
            {
                nodeService.removeAspect(nodeRef, ContentModel.ASPECT_TEMPORARY);
                log.debug("Temporary Aspect Removed");
            }
        }
        catch (IOException ioe)
        {
            throw ioe;
        }
        catch (HttpStatusCodeException hsce)
        {
            throw new GoogleDocsServiceException(hsce.getMessage(), hsce.getStatusCode().value());
        }
        catch (JSONException jsonException)
        {
            throw new GoogleDocsAuthenticationException("Unable to create activity entry: " + jsonException.getMessage(), jsonException);
        }
    }


    public void getPresentation(NodeRef nodeRef, boolean removeFromDrive)
        throws GoogleDocsAuthenticationException,
            GoogleDocsServiceException,
            IOException,
            GoogleDocsRefreshTokenException
    {
        String resourceID = nodeService.getProperty(nodeRef, GoogleDocsModel.PROP_RESOURCE_ID).toString();
        if (resourceID == null)
        {
           throw new NotInGoogleDriveException(nodeRef);
        }

        getPresentation(nodeRef, resourceID, removeFromDrive);
    }


    /*
     * (non-Javadoc)
     * 
     * @see
     * org.alfresco.integrations.google.docs.service.GoogleDocsService#getPresentation(org.alfresco.service.cmr.repository.NodeRef)
     */
    public void getPresentation(NodeRef nodeRef)
        throws GoogleDocsAuthenticationException,
            GoogleDocsServiceException,
            IOException,
            GoogleDocsRefreshTokenException
    {
        String resourceID = nodeService.getProperty(nodeRef, GoogleDocsModel.PROP_RESOURCE_ID).toString();
        if (resourceID == null)
        {
           throw new NotInGoogleDriveException(nodeRef);
        }

        getPresentation(nodeRef, resourceID, true);
    }


    /*
     * (non-Javadoc)
     * 
     * @see org.alfresco.integrations.google.docs.service.GoogleDocsService#uploadFile(org.alfresco.service.cmr.repository.NodeRef)
     */
    public DriveFile uploadFile(NodeRef nodeRef)
        throws GoogleDocsAuthenticationException,
            GoogleDocsServiceException,
            GoogleDocsRefreshTokenException,
            IOException
    {
        log.debug("Upload " + nodeRef + " to Google");
        DriveOperations driveOperations = getDriveOperations(getConnection());

        DriveFile driveFile = null;

        // It makes me want to cry that they don't support inputStreams.
        File file = null;

        try
        {
            // Get the reader
            ContentReader reader = fileFolderService.getReader(nodeRef);

            file = File.createTempFile(nodeRef.getId(), ".tmp", TempFileProvider.getTempDir());
            reader.getContent(file);

            // Get the mimetype
            FileInfo fileInfo = fileFolderService.getFileInfo(nodeRef);
            String mimetype = fileInfo.getContentData().getMimetype();

            // Create the working Directory
            DriveFile workingDir = createWorkingDirectory(nodeRef);

            driveFile = new DriveFile.Builder().setParents(workingDir.getId()).setTitle(fileInfo.getName()).setHidden(true).setMimeType(mimetype).build();

            UploadParameters uploadParameters = new UploadParameters().setConvert(true);

            driveFile = driveOperations.upload(new FileSystemResource(file), driveFile, uploadParameters);

        }
        catch (IOException ioe)
        {
            throw ioe;
        }
        catch (HttpStatusCodeException hsce)
        {
            throw new GoogleDocsServiceException(hsce.getMessage(), hsce.getStatusCode().value());
        }
        finally
        {
            if (file != null)
            {
                file.delete();
            }
        }

        return driveFile;
    }


    /**
     * Unlock and Undecorate node; Remove content from users Google Account Does not update the content in Alfresco; If content was
     * newly created by GoogleDocsService it will be removed.
     * 
     * Method can be run by owner, admin or site manager
     * 
     * @param nodeRef
     * @param driveFile
     * @param forceRemoval ignore <code>GoogleDocsServiceException</code> exceptions when attempting to remove content from user's
     * Google account
     * @throws GoogleDocsRefreshTokenException
     * @throws GoogleDocsServiceException
     * @throws GoogleDocsAuthenticationException
     */
    public void removeContent(NodeRef nodeRef, DriveFile driveFile, boolean forceRemoval)
        throws GoogleDocsAuthenticationException,
            GoogleDocsServiceException,
            GoogleDocsRefreshTokenException
    {
        if (isGoogleDocsLockOwner(nodeRef) || authorityService.hasAdminAuthority()
            || isSiteManager(nodeRef, AuthenticationUtil.getFullyAuthenticatedUser()))
        {
            unlockNode(nodeRef);

            try
            {
                deleteContent(nodeRef, driveFile); // also undecorates node
            }
            catch (GoogleDocsServiceException gdse)
            {
                if (forceRemoval)
                {
                    log.debug("There was an error (" + gdse.getMessage() + ": " + gdse.getPassedStatusCode() + ") removing "
                              + driveFile.getTitle() + " from " + AuthenticationUtil.getFullyAuthenticatedUser()
                              + "'s Google Account. Force Removal ignores the error.");
                }
                else
                {
                    throw gdse;
                }
            }

            if (nodeService.hasAspect(nodeRef, ContentModel.ASPECT_TEMPORARY))
            {
                nodeService.deleteNode(nodeRef);
            }
        }
    }


    /**
     * isSiteManager...also handles nodes not found in a site...
     * 
     * @param nodeRef
     * @param authorityName
     * @return
     */
    private boolean isSiteManager(NodeRef nodeRef, String authorityName)
    {
        boolean isSiteManager = false;

        SiteInfo siteInfo = siteService.getSite(nodeRef);

        if (siteInfo != null)
        {
            isSiteManager = SiteServiceImpl.SITE_MANAGER.equals(siteService.getMembersRole(siteInfo.getShortName(), authorityName));
        }

        return isSiteManager;
    }


    /*
     * (non-Javadoc)
     * 
     * @see
     * org.alfresco.integrations.google.docs.service.GoogleDocsService#deleteContent(org.alfresco.service.cmr.repository.NodeRef,
     * org.springframework.social.google.api.drive.DriveFile)
     */
    public boolean deleteContent(NodeRef nodeRef, DriveFile driveFile)
        throws GoogleDocsAuthenticationException,
            GoogleDocsServiceException,
            GoogleDocsRefreshTokenException
    {
        log.debug("Delete Google Doc for " + nodeRef);
        boolean deleted = false;

        DriveOperations driveOperations = getDriveOperations(getConnection());

        try
        {
            driveOperations.delete(driveFile.getId());

            // Delete the Working directory in Google Drive (if it exists....this should handle any migration issues)
            deleteWorkingDirectory(nodeRef);

            unDecorateNode(nodeRef);
            deleted = true;
        }
        catch (HttpStatusCodeException hsce)
        {
            throw new GoogleDocsServiceException(hsce.getMessage(), hsce.getStatusCode().value());
        }

        log.debug("Deleted: " + deleted);
        return deleted;
    }


    /*
     * (non-Javadoc)
     * 
     * @see
     * org.alfresco.integrations.google.docs.service.GoogleDocsService#getLatestRevision(org.alfresco.service.cmr.repository.NodeRef
     * )
     */
    public FileRevision getLatestRevision(NodeRef nodeRef)
        throws GoogleDocsAuthenticationException,
            GoogleDocsRefreshTokenException,
            GoogleDocsServiceException
    {
        FileRevision fileRevision = null;

        DriveOperations driveOperations = getDriveOperations(getConnection());

        try
        {
            if (nodeService.getProperty(nodeRef, GoogleDocsModel.PROP_RESOURCE_ID) != null)
            {
                List<FileRevision> fileRevisions = driveOperations.getRevisions(nodeService.getProperty(nodeRef, GoogleDocsModel.PROP_RESOURCE_ID).toString());

                if (fileRevisions != null)
                {
                    Collections.sort(fileRevisions, new FileRevisionComparator());

                    fileRevision = fileRevisions.get(fileRevisions.size() - 1);
                }
            }
        }
        catch (HttpStatusCodeException hsce)
        {
            throw new GoogleDocsServiceException(hsce.getMessage(), hsce.getStatusCode().value());
        }

        return fileRevision;
    }


    public FileRevision getLatestRevision(DriveFile driveFile)
        throws GoogleDocsAuthenticationException,
            GoogleDocsRefreshTokenException,
            GoogleDocsServiceException
    {
        FileRevision fileRevision = null;

        DriveOperations driveOperations = getDriveOperations(getConnection());

        List<FileRevision> fileRevisions = driveOperations.getRevisions(driveFile.getId());

        try
        {
            if (fileRevisions != null)
            {
                Collections.sort(fileRevisions, new FileRevisionComparator());

                fileRevision = fileRevisions.get(fileRevisions.size() - 1);
            }
        }
        catch (HttpStatusCodeException hsce)
        {
            throw new GoogleDocsServiceException(hsce.getMessage(), hsce.getStatusCode().value());
        }

        return fileRevision;
    }


    /*
     * (non-Javadoc)
     * 
     * @see
     * org.alfresco.integrations.google.docs.service.GoogleDocsService#decorateNode(org.alfresco.service.cmr.repository.NodeRef,
     * org.springframework.social.google.api.drive.DriveFile, boolean)
     */
    public void decorateNode(NodeRef nodeRef, DriveFile driveFile, boolean newcontent)
    {
        decorateNode(nodeRef, driveFile, null, null, newcontent);
    }


    /*
     * (non-Javadoc)
     * 
     * @see
     * org.alfresco.integrations.google.docs.service.GoogleDocsService#decorateNode(org.alfresco.service.cmr.repository.NodeRef,
     * org.springframework.social.google.api.drive.DriveFile, org.springframework.social.google.api.drive.FileRevision, boolean)
     */
    public void decorateNode(NodeRef nodeRef, DriveFile driveFile, FileRevision fileRevision, boolean newcontent)
    {
        decorateNode(nodeRef, driveFile, fileRevision, null, newcontent);
    }


    /*
     * (non-Javadoc)
     * 
     * @see
     * org.alfresco.integrations.google.docs.service.GoogleDocsService#decorateNode(org.alfresco.service.cmr.repository.NodeRef,
     * org.springframework.social.google.api.drive.DriveFile, org.springframework.social.google.api.drive.FileRevision,
     * java.util.List<org.alfresco.integrations.google.docs.service.GoogleDocsService.GooglePermission>, boolean)
     */
    public void decorateNode(NodeRef nodeRef, DriveFile driveFile, FileRevision fileRevision, List<GooglePermission> permissions, boolean newcontent)
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
            aspectProperties.put(GoogleDocsModel.PROP_CURRENT_PERMISSIONS, buildPermissionsPropertyValue(permissions));
            aspectProperties.put(GoogleDocsModel.PROP_RESOURCE_ID, driveFile.getId());
            aspectProperties.put(GoogleDocsModel.PROP_EDITORURL, driveFile.getAlternateLink());
            aspectProperties.put(GoogleDocsModel.PROP_DRIVE_WORKING_FOLDER, driveFile.getParents().get(0).getId());
            if (fileRevision != null)
            {
                aspectProperties.put(GoogleDocsModel.PROP_REVISION_ID, fileRevision.getId());
            }
            if (!nodeService.hasAspect(nodeRef, GoogleDocsModel.ASPECT_EDITING_IN_GOOGLE))
            {
                nodeService.addAspect(nodeRef, GoogleDocsModel.ASPECT_EDITING_IN_GOOGLE, aspectProperties);
            }
            else
            {
                for (Map.Entry<QName, Serializable> prop : aspectProperties.entrySet())
                {
                    nodeService.setProperty(nodeRef, prop.getKey(), prop.getValue());
                }
            }
            log.debug("Resource Id: " + aspectProperties.get(GoogleDocsModel.PROP_RESOURCE_ID));
            log.debug("Editor Url:" + aspectProperties.get(GoogleDocsModel.PROP_EDITORURL));
            log.debug("Revision Id: "
                      + ((fileRevision != null) ? aspectProperties.get(GoogleDocsModel.PROP_REVISION_ID)
                                               : "No file revision provided"));
        }
        finally
        {
            behaviourFilter.enableBehaviour(nodeRef, ContentModel.ASPECT_VERSIONABLE);
        }
    }


    /*
     * (non-Javadoc)
     * 
     * @see
     * org.alfresco.integrations.google.docs.service.GoogleDocsService#unDecorateNode(org.alfresco.service.cmr.repository.NodeRef)
     */
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


    /*
     * (non-Javadoc)
     * 
     * @see org.alfresco.integrations.google.docs.service.GoogleDocsService#lockNode(org.alfresco.service.cmr.repository.NodeRef)
     */
    public void lockNode(NodeRef nodeRef)
    {
        if (nodeService.getProperty(nodeRef, GoogleDocsModel.PROP_LOCKED) == null || 
                new Boolean(false).equals(nodeService.getProperty(nodeRef, GoogleDocsModel.PROP_LOCKED)))
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
    }


    /*
     * (non-Javadoc)
     * 
     * @see org.alfresco.integrations.google.docs.service.GoogleDocsService#unlockNode(org.alfresco.service.cmr.repository.NodeRef)
     */
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
        Boolean isNodeLocked = (Boolean)nodeService.getProperty(nodeRef, GoogleDocsModel.PROP_LOCKED);

        if (isNodeLocked != null && isNodeLocked.booleanValue())
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
            //append the x needed in the filename
            name = name.concat("x");
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

        // Get the last known node with the same name (+number) in the same folder
        NodeRef lastDup = findLastDuplicate(nodeRef, name);

        if (lastDup != null)
        {
            // if it is not the same file increment (or add number to) the filename
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
            GoogleDocsServiceException
    {
        log.debug("Check for Concurrent Editors (Edits that have occured in the last " + idleThreshold + " seconds)");
        DriveOperations driveOperations = getDriveOperations(getConnection());

        String resourceID = nodeService.getProperty(nodeRef, GoogleDocsModel.PROP_RESOURCE_ID).toString();

        boolean concurrentChange = false;

        try
        {
            List<FileRevision> fileRevisionList = driveOperations.getRevisions(resourceID.substring(resourceID.lastIndexOf(':') + 1));

            if (fileRevisionList.size() > 1)
            {
                log.debug("Revisions Found");
                Collections.sort(fileRevisionList, Collections.reverseOrder(new FileRevisionComparator()));

                // Find any revisions occurring within the last 'idleThreshold'
                // seconds
                List<FileRevision> workingList = new ArrayList<FileRevision>();

                Calendar bufferTime = Calendar.getInstance();
                bufferTime.add(Calendar.SECOND, -idleThreshold);

                for (FileRevision entry : fileRevisionList)
                {
                    if (entry.getModifiedDate().after(new Date(bufferTime.getTimeInMillis())))
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
                        FileRevision fileRevision = workingList.get(i);
                        String name = getGoogleUserProfile().getName();

                        // if there is no author -- the entry is the initial
                        // creation
                        if (fileRevision.getLastModifyingUserName() != null)
                        {
                            if (fileRevision.getLastModifyingUserName().equals(name))
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
                String name = getGoogleUserProfile().getName();

                // if the authors list is empty -- the author was the original
                // creator and it is the initial copy
                if (fileRevisionList.get(0).getLastModifyingUserName() != null)
                {

                    if (!fileRevisionList.get(0).getLastModifyingUserName().equals(name))
                    {
                        Calendar bufferTime = Calendar.getInstance();
                        bufferTime.add(Calendar.SECOND, -idleThreshold);

                        if (fileRevisionList.get(0).getModifiedDate().before(new Date(bufferTime.getTimeInMillis())))
                        {
                            log.debug("Revisions not made by current user found.");
                            concurrentChange = true;
                        }
                    }
                }
            }

        }
        catch (HttpStatusCodeException hsce)
        {
            throw new GoogleDocsServiceException(hsce.getMessage(), hsce.getStatusCode().value());
        }

        log.debug("Concurrent Edits: " + concurrentChange);
        return concurrentChange;
    }


    public DriveFile getDriveFile(String resourceID)
        throws GoogleDocsServiceException,
            GoogleDocsAuthenticationException,
            GoogleDocsRefreshTokenException
    {
        log.debug("Get Document list entry for resource " + resourceID);
        DriveOperations driveOperations = getDriveOperations(getConnection());

        DriveFile driveFile = null;

        try
        {
            driveFile = driveOperations.getFile(resourceID.substring(resourceID.lastIndexOf(':') + 1));
        }
        catch (HttpStatusCodeException hsce)
        {
            throw new GoogleDocsServiceException(hsce.getMessage(), hsce.getStatusCode().value());
        }

        return driveFile;
    }


    public DriveFile getDriveFile(NodeRef nodeRef)
          throws GoogleDocsServiceException,
              GoogleDocsAuthenticationException,
              GoogleDocsRefreshTokenException
      {
          String resourceID = nodeService.getProperty(nodeRef, GoogleDocsModel.PROP_RESOURCE_ID).toString();
          log.debug("Node " + nodeRef + " maps to Resource ID " + resourceID);

          if (resourceID == null)
          {
             throw new NotInGoogleDriveException(nodeRef);
          }
          
          return getDriveFile(resourceID);
      }


    private String getExportLink(DriveFile driveFile, String mimetype)
    {
        Map<String, String> exportLinks = driveFile.getExportLinks();

        return exportLinks.get(validateMimeType(mimetype));
    }


    private InputStream getFileInputStream(DriveFile driveFile, String mimetype)
        throws GoogleDocsAuthenticationException,
            GoogleDocsRefreshTokenException,
            GoogleDocsServiceException
    {
        RestTemplate restTemplate = new RestTemplate();

        String url = getExportLink(driveFile, mimetype);
        log.debug("Google Export Format (mimetype) link: " + url);
        
        if (url == null)
        {
            throw new GoogleDocsServiceException("Google Docs Export Format not found.", HttpStatus.SC_UNSUPPORTED_MEDIA_TYPE);
        }

        HttpHeaders headers = new HttpHeaders();
        headers.add("Authorization", "Bearer " + getConnection().getApi().getAccessToken());
        MultiValueMap<String, Object> body = new LinkedMultiValueMap<String, Object>();
        HttpEntity<MultiValueMap<String, Object>> entity = new HttpEntity<MultiValueMap<String, Object>>(body, headers);

        ResponseEntity<byte[]> response = restTemplate.exchange(url, HttpMethod.GET, entity, byte[].class);

        return new ByteArrayInputStream(response.getBody());
    }


    public GoogleUserProfile getGoogleUserProfile()
        throws GoogleDocsAuthenticationException,
            GoogleDocsRefreshTokenException,
            GoogleDocsServiceException
    {
        log.debug("Get Google Docs user metadata");
        UserInfoOperations userInfoOperations = getConnection().getApi().userOperations();

        GoogleUserProfile googleUserProfile = null;

        try
        {
            googleUserProfile = userInfoOperations.getUserProfile();
        }
        catch (HttpStatusCodeException hsce)
        {
            throw new GoogleDocsServiceException(hsce.getMessage(), hsce.getStatusCode().value());
        }

        return googleUserProfile;
    }


    private void postActivity(NodeRef nodeRef)
        throws JSONException
    {
        log.debug("Create Activity Stream Entry");
        if (personService.personExists(AuthenticationUtil.getRunAsUser()))
        {
            try
            {
                SiteInfo siteInfo = siteService.getSite(nodeRef);
                if (siteInfo != null)
                {
                    String activityType = FILE_UPDATED;
                    if (nodeService.hasAspect(nodeRef, ContentModel.ASPECT_TEMPORARY))
                    {
                        activityType = FILE_ADDED;
                    }

                    String siteId = siteInfo.getShortName();

                    JSONObject jsonActivityData = new JSONObject();

                    PersonInfo personInfo = personService.getPerson(personService.getPerson(AuthenticationUtil.getRunAsUser(), false));

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
                else
                {
                    log.debug("Activity stream entry not created -- node is not inside a site.");
                }
            }
            catch (JSONException jsonException)
            {
                throw jsonException;
            }
        }
        else
        {
            log.debug("Activity stream entry not created -- user does not exist.");
        }
    }


    /**
     * Return a GData resource list entry representing the item with the given ID
     * 
     * TODO: Migrate fully to Google Drive API and Spring Social Google when the Drive API supports all the capabilities we require
     * 
     * @param resourceID Identifier for the file on Google
     * @return
     * @throws IOException
     * @throws GoogleDocsServiceException
     * @throws GoogleDocsAuthenticationException
     * @throws GoogleDocsRefreshTokenException
     */
    private DocumentListEntry getDocumentListEntry(String resourceID)
        throws IOException,
            GoogleDocsServiceException,
            GoogleDocsAuthenticationException,
            GoogleDocsRefreshTokenException
    {
        log.debug("Get Document list entry for resource " + resourceID);
        DocumentListEntry documentListEntry = null;

        try
        {
            documentListEntry = getDocsService(getConnection()).getEntry(new URL(GoogleDocsConstants.URL_BASE_FEED
                                                                                 + "/"
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


    /**
     * Construct a GData service instance for use by methods which require this.
     * 
     * <p>
     * It is expected that the user is authenticated when this method is called. The authentication from the supplied connection
     * will be applied to the service.
     * </p>
     * 
     * <p>
     * TODO: Migrate fully to Google Drive API and Spring Social Google when the Drive API supports all the capabilities we require
     * <p>
     * 
     * @return GData Service instance
     */
    private DocsService getDocsService(Connection<Google> connection)
        throws GoogleDocsServiceException,
            GoogleDocsAuthenticationException,
            GoogleDocsRefreshTokenException
    {
        DocsService docsService = new DocsService(GoogleDocsConstants.APPLICATION_NAME);

        Google google = connection.getApi(); // after authentication
        google.applyAuthentication(docsService);

        log.debug("Google Docs Client initiated");
        return docsService;

    }


    /**
     * Retrieve the file's ACL from Google and return a list of users who are listed in the ACL along with their roles.
     * 
     * @param fileId Identifier for the file on Google
     * @throws GoogleDocsAuthenticationException
     * @throws GoogleDocsRefreshTokenException
     * @throws GoogleDocsServiceException
     * @throws IOException
     * @return Map where each represents a username and the value the role name
     */
    private List<GooglePermission> getFilePermissions(String resourceId)
        throws GoogleDocsAuthenticationException,
            GoogleDocsRefreshTokenException,
            GoogleDocsServiceException,
            IOException
    {
        if (log.isDebugEnabled())
        {
            log.error("Looking up Google user profile");
        }
        GoogleUserProfile profile = getGoogleUserProfile();

        log.debug("Fetching permissions for file with resource ID " + resourceId);

        // TODO The Drive API does not yet return the email/username of document collborators.
        // Use the Drive API when they make this possible!

        // DriveOperations driveOperations = getDriveOperations(getConnection());
        // List<UserPermission> permissions = driveOperations.getPermissions(fileId);
        // Get the googleMetadata to reference the Node
        // ArrayList<String> permissionsList = new ArrayList<String>(permissions.size());
        // for (UserPermission userPermission : permissions)
        // {
        // permissionsList.add(userPermission.getName() + "|" + userPermission.getRole().name());
        // }

        List<GooglePermission> permissionsMap = new ArrayList<GooglePermission>();

        try
        {
            DocumentListEntry documentListEntry = getDocumentListEntry(resourceId);
            AclFeed aclFeed = getDocsService(getConnection()).getFeed(new URL(documentListEntry.getAclFeedLink().getHref()), AclFeed.class);
            for (AclEntry entry : aclFeed.getEntries())
            {
                String role = entry.getRole().getValue(), scope = entry.getScope().getValue();
                Type type = entry.getScope().getType();
                String xmlBody = entry.getXmlBlob().getBlob();
                if (xmlBody != null)
                {
                    log.debug("Found XML body: " + entry.getXmlBlob().getBlob());
                }
                if (xmlBody != null && xmlBody.indexOf("<gAcl:additionalRole value='commenter'/>") > -1 &&
                        role.equals(AclRole.READER.getValue()))
                {
                    role = AclRole.COMMENTER.getValue();
                }
                if (type.equals(Type.USER) && profile.getEmail().equals(scope))
                {
                    if (log.isDebugEnabled())
                    {
                        log.debug("Skipping permission for owner '" + scope + "' (" + type + ") as '" + role
                                  + "' which is implicit");
                    }
                }
                else
                {
                    if (log.isDebugEnabled())
                    {
                        log.debug("Adding permission for '" + scope + "' (" + type + ") as '" + role + "'");
                    }
                    // Include the scope (authority identifier, e.g. email address), type (e.g. "user") and role (e.g. "owner")
                    // Store the type lowercase for consistency with the Drive API v1.0 permission entity
                    // (https://developers.google.com/drive/v2/reference/permissions)
                    permissionsMap.add(new GooglePermission(scope, type.name().toLowerCase(), role));
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

        return permissionsMap;
    }


    /**
     * Look up information from Google that describes the current state of the document, and store this into the repository.
     * 
     * <p>
     * It is intended that this should be called prior to deleting the document from Google, and allows the state to be re-applied
     * if the content is subsequently edited again in Google.
     * </p>
     * 
     * <p>
     * At present this stores only information on which Google users were explicitly listed as collaborators on the document.
     * </p>
     * 
     * @param nodeRef Noderef identifying the file in the repository
     * @param fileId Identifier for the file on Google
     * @throws GoogleDocsAuthenticationException
     * @throws GoogleDocsRefreshTokenException
     * @throws GoogleDocsServiceException
     * @throws IOException
     */
    private void saveSharedInfo(NodeRef nodeRef, String resourceId)
        throws GoogleDocsAuthenticationException,
            GoogleDocsRefreshTokenException,
            GoogleDocsServiceException,
            IOException
    {
        List<GooglePermission> permissionsMap = getFilePermissions(resourceId);
        Serializable permissionsList = buildPermissionsPropertyValue(permissionsMap);
        Map<QName, Serializable> aspectProperties = new HashMap<QName, Serializable>();
        aspectProperties.put(GoogleDocsModel.PROP_PERMISSIONS, permissionsList);
        log.debug("File permissions: " + permissionsList);

        behaviourFilter.disableBehaviour(nodeRef, ContentModel.ASPECT_VERSIONABLE);
        try
        {
            if (nodeService.hasAspect(nodeRef, GoogleDocsModel.ASPECT_SHARED_IN_GOOGLE))
            {
                log.debug("Updating Shared Google Docs permissions on " + nodeRef);
                nodeService.setProperty(nodeRef, GoogleDocsModel.PROP_PERMISSIONS, permissionsList);
            }
            else
            {
                log.debug("Adding Shared Google Docs aspect to " + nodeRef);
                nodeService.addAspect(nodeRef, GoogleDocsModel.ASPECT_SHARED_IN_GOOGLE, aspectProperties);
            }
        }
        finally
        {
            behaviourFilter.enableBehaviour(nodeRef, ContentModel.ASPECT_VERSIONABLE);
        }
    }


    public Serializable buildPermissionsPropertyValue(List<GooglePermission> permissions)
    {
        if (permissions == null)
        {
            return null;
        }
        ArrayList<String> permissionsList = new ArrayList<String>(permissions.size());
        for (GooglePermission p : permissions)
        {
            permissionsList.add(p.getAuthorityType() + "|" + p.getAuthorityId() + "|" + p.getRoleName());
        }
        return permissionsList;
    }


    /**
     * List the saved Google permissions currently stored for this object.
     * 
     * @param nodeRef Noderef identifying the file in the repository
     * @return A list of permissions objects stored for this node, which may be an empty list, or null if nothing is stored
     */
    public List<GooglePermission> getGooglePermissions(NodeRef nodeRef, QName qName)
    {
        if (log.isDebugEnabled())
        {
            log.error("Loading Google permissions for " + nodeRef);
        }
        @SuppressWarnings("unchecked")
        List<String> propVals = (List<String>)nodeService.getProperty(nodeRef, qName);
        if (propVals != null)
        {
            List<GooglePermission> permissions = new ArrayList<GooglePermission>(propVals.size());
            for (String val : propVals)
            {
                try
                {
                    if (log.isDebugEnabled())
                    {
                        log.error("Adding Google permission '" + val + "' for " + nodeRef);
                    }
                    permissions.add(GooglePermission.fromString(val));
                }
                catch (IllegalArgumentException e)
                {
                    log.error("Skipping bad permission '" + val + "'");
                }
            }
            return permissions;
        }
        else
        {
            if (log.isDebugEnabled())
            {
                log.error("No Google permissions found for " + nodeRef);
            }
        }
        return null;
    }


    @Override
    public void addRemotePermissions(DriveFile driveFile, List<GooglePermission> permissions, boolean sendEmail)
        throws GoogleDocsAuthenticationException,
            GoogleDocsServiceException,
            GoogleDocsRefreshTokenException
    {
        if (log.isDebugEnabled())
        {
            log.debug("Adding permissions on item " + driveFile.getId() + " in Google");
            if (sendEmail)
            {
                log.debug("Notification emails will be sent");
            }
            else
            {
                log.debug("Notification emails will NOT be sent");
            }
        }

        DriveOperations driveOperations = getDriveOperations(getConnection());
        for (GooglePermission p : permissions)
        {
            String roleName = p.getRoleName(), authorityType = p.getAuthorityType();
            PermissionRole role;
            PermissionType type;
            List<AdditionalRole> additionalRoles = new ArrayList<AdditionalRole>();
            if (roleName.equals(PERMISSION_ROLE_READER))
            {
                role = PermissionRole.READER;
            }
            else if (roleName.equals(PERMISSION_ROLE_WRITER))
            {
                role = PermissionRole.WRITER;
            }
            else if (roleName.equals(PERMISSION_ROLE_OWNER))
            {
                role = PermissionRole.OWNER;
            }
            else if (roleName.equals(PERMISSION_ROLE_COMMENTER))
            {
                role = PermissionRole.READER;
                additionalRoles.add(AdditionalRole.COMMENTER);
            }
            else
            {
                throw new IllegalArgumentException("Bad permission role " + roleName);
            }

            if (authorityType.equals(PERMISSION_TYPE_USER))
            {
                type = PermissionType.USER;
            }
            else if (authorityType.equals(PERMISSION_TYPE_GROUP))
            {
                type = PermissionType.GROUP;
            }
            else if (authorityType.equals(PERMISSION_TYPE_DOMAIN))
            {
                type = PermissionType.DOMAIN;
            }
            else if (authorityType.equals(PERMISSION_TYPE_ANYONE))
            {
                type = PermissionType.ANYONE;
            }
            else
            {
                throw new IllegalArgumentException("Bad permission type " + authorityType);
            }
            if (log.isDebugEnabled())
            {
                log.debug("Adding permission " + role + " for " + type + " " + p.getAuthorityId() + "");
            }

            driveOperations.addPermission(driveFile.getId(), new UserPermission(role, type, additionalRoles, p.getAuthorityId()), sendEmail);
        }
    }


    private DriveFile createWorkingDirectory(NodeRef nodeRef)
        throws GoogleDocsAuthenticationException,
            GoogleDocsRefreshTokenException,
            GoogleDocsServiceException
    {
        DriveFile driveFile = null;

        // Get or create the parent folder
        if (googleDriveFolderExists(GoogleDocsConstants.ROOT_FOLDER_ID, GoogleDocsConstants.ALF_TEMP_FOLDER))
        {
            List<DriveFile> driveFiles = getFolder(GoogleDocsConstants.ROOT_FOLDER_ID, GoogleDocsConstants.ALF_TEMP_FOLDER);

            // Look for our description if there is more than one file returned
            if (!driveFiles.isEmpty() && driveFiles.size() > 1)
            {
                for (DriveFile file : driveFiles)
                {
                    if (StringUtils.equals(file.getDescription(), GoogleDocsConstants.ALF_TEMP_FOLDER_DESC))
                    {
                        driveFile = file;
                        break;
                    }
                }

                if (driveFile == null)
                {
                    driveFile = createFolder(GoogleDocsConstants.ROOT_FOLDER_ID, GoogleDocsConstants.ALF_TEMP_FOLDER);

                    DriveOperations driveOperations = getDriveOperations(getConnection());
                    driveFile = driveOperations.updateFileMetadata(driveFile.getId(), null, null, GoogleDocsConstants.ALF_TEMP_FOLDER_DESC);
                }
            }
            else if (!driveFiles.isEmpty() && driveFiles.size() == 1)
            {
                driveFile = driveFiles.get(0);
            }
        } else {
            driveFile = createFolder(GoogleDocsConstants.ROOT_FOLDER_ID, GoogleDocsConstants.ALF_TEMP_FOLDER);

            DriveOperations driveOperations = getDriveOperations(getConnection());
            driveFile = driveOperations.updateFileMetadata(driveFile.getId(), null, null, GoogleDocsConstants.ALF_TEMP_FOLDER_DESC);
        }

        // create working directory
        driveFile = createFolder(driveFile.getId(), nodeRef.getId());

        return driveFile;
    }


    private void deleteWorkingDirectory(NodeRef nodeRef)
        throws GoogleDocsAuthenticationException,
            GoogleDocsRefreshTokenException,
            GoogleDocsServiceException
    {
        if (nodeService.getProperty(nodeRef, GoogleDocsModel.PROP_DRIVE_WORKING_FOLDER) != null
            && StringUtils.isNotBlank(nodeService.getProperty(nodeRef, GoogleDocsModel.PROP_DRIVE_WORKING_FOLDER).toString()))
        {
            String id = nodeService.getProperty(nodeRef, GoogleDocsModel.PROP_DRIVE_WORKING_FOLDER).toString();
            deleteFolder(id);
        }
    }


    /**
     * Does a folder with the name and in the parent folder exist. (Note: there may be more than one)
     * 
     * @param parentId
     * @param folderName
     * @return
     * @throws GoogleDocsAuthenticationException
     * @throws GoogleDocsRefreshTokenException
     * @throws GoogleDocsServiceException
     */
    private boolean googleDriveFolderExists(String parentId, String folderName)
        throws GoogleDocsAuthenticationException,
            GoogleDocsRefreshTokenException,
            GoogleDocsServiceException
    {
        boolean exists = false;

        List<DriveFile> driveFiles = getFolder(parentId, folderName);

        if (!driveFiles.isEmpty())
        {
            exists = true;
        }

        return exists;
    }


    /**
     * Create new folder in Google Drive
     * 
     * @param parentId
     * @param folderName
     * @return
     * @throws GoogleDocsServiceException
     * @throws GoogleDocsAuthenticationException
     * @throws GoogleDocsRefreshTokenException
     */
    private DriveFile createFolder(String parentId, String folderName)
        throws GoogleDocsServiceException,
            GoogleDocsAuthenticationException,
            GoogleDocsRefreshTokenException
    {
        DriveFile driveFile = null;
        DriveOperations driveOperations = getDriveOperations(getConnection());

        try
        {
            driveFile = driveOperations.createFolder(parentId, folderName);
            driveFile = driveOperations.hide(driveFile.getId());
        }
        catch (HttpStatusCodeException hsce)
        {
            throw new GoogleDocsServiceException(hsce.getMessage(), hsce, hsce.getStatusCode().value());
        }

        return driveFile;
    }


    private List<DriveFile> getFolder(String parentId, String folderName)
        throws GoogleDocsAuthenticationException,
            GoogleDocsRefreshTokenException,
            GoogleDocsServiceException
    {

        List<DriveFile> driveFiles = new ArrayList<DriveFile>();
        DriveFilesPage page = null;
        DriveOperations driveOperations = getDriveOperations(getConnection());

        try
        {
            do
            {
                if (page == null)
                {
                    page = driveOperations.driveFileQuery().titleIs(folderName).isFolder().getPage();
                }
                else
                {
                    page = driveOperations.driveFileQuery().fromPage(page.getNextPageToken()).getPage();
                }

                List<DriveFile> childfolders = page.getItems();
                if (childfolders != null && !childfolders.isEmpty())
                {
                    driveFiles.addAll(childfolders);
                }
            }
            while (page.getNextPageToken() != null);
        }
        catch (HttpStatusCodeException hsce)
        {
            throw new GoogleDocsServiceException(hsce.getMessage(), hsce, hsce.getStatusCode().value());
        }

        return driveFiles;
    }


    /**
     * Delete Google Drive Folder
     * 
     * @param folderId
     * @throws GoogleDocsAuthenticationException
     * @throws GoogleDocsRefreshTokenException
     * @throws GoogleDocsServiceException
     */
    private void deleteFolder(String folderId)
        throws GoogleDocsAuthenticationException,
            GoogleDocsRefreshTokenException,
            GoogleDocsServiceException
    {
        DriveOperations driveOperations = getDriveOperations(getConnection());

        try
        {
            driveOperations.delete(folderId);
        }
        catch (HttpStatusCodeException hsce)
        {
            throw new GoogleDocsServiceException(hsce.getMessage(), hsce, hsce.getStatusCode().value());
        }
    }
}
