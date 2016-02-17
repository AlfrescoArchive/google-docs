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

package org.alfresco.integrations.google.docs.service;


import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Serializable;
import java.util.ArrayList;
import java.util.Arrays;
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

import com.google.api.client.auth.oauth2.BearerToken;
import com.google.api.client.auth.oauth2.ClientParametersAuthentication;
import com.google.api.client.auth.oauth2.Credential;
import com.google.api.client.auth.oauth2.TokenResponseException;
import com.google.api.client.googleapis.auth.oauth2.GoogleAuthorizationCodeFlow;
import com.google.api.client.googleapis.auth.oauth2.GoogleAuthorizationCodeRequestUrl;
import com.google.api.client.googleapis.auth.oauth2.GoogleClientSecrets;
import com.google.api.client.googleapis.auth.oauth2.GoogleTokenResponse;
import com.google.api.client.googleapis.json.GoogleJsonResponseException;
import com.google.api.client.http.FileContent;
import com.google.api.client.http.GenericUrl;
import com.google.api.client.http.HttpResponse;
import com.google.api.client.http.HttpTransport;
import com.google.api.client.http.javanet.NetHttpTransport;
import com.google.api.client.json.jackson2.JacksonFactory;
import com.google.api.services.drive.Drive;
import com.google.api.services.drive.model.About;
import com.google.api.services.drive.model.File;
import com.google.api.services.drive.model.FileList;
import com.google.api.services.drive.model.ParentReference;
import com.google.api.services.drive.model.Permission;
import com.google.api.services.drive.model.PermissionList;
import com.google.api.services.drive.model.Revision;
import com.google.api.services.drive.model.RevisionList;
import com.google.api.services.drive.model.User;
import com.google.api.services.oauth2.Oauth2;
import com.google.api.services.oauth2.model.Userinfoplus;
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
import org.alfresco.repo.lock.mem.LockState;
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
import org.alfresco.service.cmr.repository.AspectMissingException;
import org.alfresco.service.cmr.repository.ContentReader;
import org.alfresco.service.cmr.repository.ContentWriter;
import org.alfresco.service.cmr.repository.MimetypeService;
import org.alfresco.service.cmr.repository.NodeRef;
import org.alfresco.service.cmr.repository.NodeService;
import org.alfresco.service.cmr.repository.Path;
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
import org.springframework.core.io.Resource;


/**
 * @author Jared Ottley <jared.ottley@alfresco.com>
 */


/**
 * @author jottley
 */
public class GoogleDocsServiceImpl
        implements GoogleDocsService
{
    private static final Log log = LogFactory.getLog(GoogleDocsServiceImpl.class);

    // Services
    private OAuth2CredentialsStoreService oauth2CredentialsStoreService;

    private HttpTransport  httpTransport;
    private JacksonFactory jsonFactory;

    private GoogleClientSecrets clientSecrets;


    private FileFolderService fileFolderService;
    private NodeService       nodeService;
    private LockService       lockservice;
    private MimetypeService   mimetypeService;
    private BehaviourFilter   behaviourFilter;
    private ActivityService   activityService;
    private SiteService       siteService;
    private TenantService     tenantService;
    private PersonService     personService;
    private AuthorityService  authorityService;

    private DictionaryService dictionaryService;
    private FileNameUtil      filenameUtil;

    // Property Mappings
    private Map<String, String>              importFormats     = new HashMap<String, String>();
    private Map<String, Map<String, String>> exportFormats     = new HashMap<String, Map<String, String>>();
    private Map<String, String>              upgradeMappings   = new HashMap<String, String>();
    private Map<String, String>              downgradeMappings = new HashMap<String, String>();

    // New Content
    private Resource newDocument;
    private Resource newSpreadsheet;
    private Resource newPresentation;

    // Time (in seconds) between last edit and now to consider edits as
    // concurrent
    private int idleThreshold = 0;

    private boolean enabled = true;

    // Activities
    private static final String FILE_ADDED   = "org.alfresco.documentlibrary.file-added";
    private static final String FILE_UPDATED = "org.alfresco.documentlibrary.file-updated";


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
            if (log.isDebugEnabled())
            {
                log.debug("Mimetype " + mimetype + " cannot be exported directly and will be upgraded");
            }
            throw new MustUpgradeFormatException();
        }
        else if (isDownGrade(mimetype))
        {
            if (log.isDebugEnabled())
            {
                log.debug("Mimetype " + mimetype + " cannot be exported directly and will be upgraded");
            }
            throw new MustDowngradeFormatException();
        }
        else
        {
            if (log.isDebugEnabled())
            {
                log.debug("Mimetype " + mimetype + " can be exported");
            }
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
            if (log.isDebugEnabled())
            {
                log.debug("Mimetype will be downgraded to " + mimeType);
            }
        }
        else if (isUpgrade(mimeType))
        {
            mimeType = upgradeMappings.get(mimeType);
            if (log.isDebugEnabled())
            {
                log.debug("Mimetype will be upgraded to " + mimeType);
            }
        }

        return mimeType;
    }


    public void init()
            throws IOException
    {
        httpTransport = new NetHttpTransport();
        jsonFactory = new JacksonFactory();

        clientSecrets = GoogleClientSecrets.load(jsonFactory, new InputStreamReader(GoogleDocsServiceImpl.class.getResourceAsStream("client_secret.json")));
    }


    private GoogleAuthorizationCodeFlow getFlow()
            throws IOException
    {
        return new GoogleAuthorizationCodeFlow.Builder(httpTransport, jsonFactory, clientSecrets, GoogleDocsConstants.SCOPES).setAccessType("offline").setApprovalPrompt("auto").build();
    }


    public Credential getCredential()
            throws GoogleDocsAuthenticationException,
            GoogleDocsRefreshTokenException,
            GoogleDocsServiceException,
            IOException
    {
        Credential credential = null;

        // OAuth credentials for the current user, if the exist
        OAuth2CredentialsInfo credentialInfo = oauth2CredentialsStoreService.getPersonalOAuth2Credentials(GoogleDocsConstants.REMOTE_SYSTEM);

        if (credentialInfo != null)
        {
            log.debug("OAuth Credentials Exist for " + AuthenticationUtil.getFullyAuthenticatedUser());

            credential = new Credential.Builder(BearerToken.authorizationHeaderAccessMethod()).setJsonFactory(jsonFactory).setTransport(httpTransport).setClientAuthentication(new ClientParametersAuthentication(clientSecrets.getDetails().getClientId(), clientSecrets.getDetails().getClientSecret())).setTokenServerEncodedUrl(clientSecrets.getDetails().getTokenUri()).build();
            credential.setAccessToken(credentialInfo.getOAuthAccessToken()).setRefreshToken(credentialInfo.getOAuthRefreshToken()).setExpirationTimeMilliseconds(credentialInfo.getOAuthTicketExpiresAt().getTime());

            try
            {
                log.debug("Test oAuth Credentials for " + AuthenticationUtil.getFullyAuthenticatedUser());
                testConnection(credential);
            }
            catch (GoogleJsonResponseException e)
            {
                if (e.getStatusCode() == HttpStatus.SC_UNAUTHORIZED)
                {
                    try
                    {
                        credential = refreshAccessToken();
                        testConnection(credential);
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
                    throw new GoogleDocsServiceException(e.getMessage(), e.getStatusCode(), e);
                }
            }
            catch (TokenResponseException e)
            {
                try
                {
                    credential = refreshAccessToken();
                    testConnection(credential);
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
            catch (GoogleDocsServiceException e)
            {
                throw e;
            }
        }

        log.debug("Google Docs Credentials Created.");
        return credential;
    }


    private void testConnection(Credential credential)
            throws GoogleJsonResponseException,
            TokenResponseException,
            GoogleDocsServiceException
    {
        Oauth2 userInfoService = new Oauth2.Builder(new NetHttpTransport(), new JacksonFactory(), credential).setApplicationName(GoogleDocsConstants.APPLICATION_NAME).build();
        Userinfoplus userInfo = null;
        try
        {
            userInfo = userInfoService.userinfo().get().execute();
        }
        catch (TokenResponseException e)
        {
            //rethrow before it hits IOException (parent object of TokenResponseException
            throw e;
        }
        catch (IOException e)
        {
            throw new GoogleDocsServiceException("Error creating Connection: " + e.getMessage(), e);
        }
        if (userInfo == null || userInfo.getId() == null)
        {
            throw new GoogleDocsServiceException("Error creating Connection: No user");
        }
    }


    private Credential refreshAccessToken()
            throws GoogleDocsAuthenticationException,
            GoogleDocsRefreshTokenException,
            GoogleDocsServiceException,
            IOException
    {
        log.debug("Refreshing Access Token for " + AuthenticationUtil.getFullyAuthenticatedUser());
        OAuth2CredentialsInfo credentialInfo = oauth2CredentialsStoreService.getPersonalOAuth2Credentials(GoogleDocsConstants.REMOTE_SYSTEM);

        if (credentialInfo.getOAuthRefreshToken() != null)
        {
            Credential credential = null;
            boolean success = false;
            try
            {
                credential = new Credential.Builder(BearerToken.authorizationHeaderAccessMethod()).setJsonFactory(jsonFactory).setTransport(httpTransport).setClientAuthentication(new ClientParametersAuthentication(clientSecrets.getDetails().getClientId(), clientSecrets.getDetails().getClientSecret())).setTokenServerEncodedUrl(clientSecrets.getDetails().getTokenUri()).build();
                credential.setAccessToken(credentialInfo.getOAuthAccessToken()).setRefreshToken(credentialInfo.getOAuthRefreshToken()).setExpirationTimeMilliseconds(credentialInfo.getOAuthTicketExpiresAt().getTime());

                success = credential.refreshToken();
            }
            catch (GoogleJsonResponseException e)
            {
                if (e.getStatusCode() == HttpStatus.SC_BAD_REQUEST)
                {
                    throw new GoogleDocsAuthenticationException(e.getMessage());
                }
                else if (e.getStatusCode() == HttpStatus.SC_UNAUTHORIZED)
                {
                    throw new GoogleDocsAuthenticationException("Token Refresh Failed.");
                }
                else
                {
                    throw new GoogleDocsServiceException(e.getMessage(), e.getStatusCode(), e);
                }

            }

            if (credential != null && success)
            {
                Date expiresIn = null;

                if (credential.getExpirationTimeMilliseconds() != null)
                {
                    if (credential.getExpirationTimeMilliseconds() > 0L)
                    {
                        expiresIn = new Date(new Date().getTime() + credential.getExpirationTimeMilliseconds());
                    }
                }

                try
                {
                    oauth2CredentialsStoreService.storePersonalOAuth2Credentials(GoogleDocsConstants.REMOTE_SYSTEM, credential.getAccessToken(), credential.getRefreshToken(), expiresIn, new Date());
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
            return credential;

        }
        else
        {
            throw new GoogleDocsRefreshTokenException(
                    "No Refresh Token Provided for " + AuthenticationUtil.getFullyAuthenticatedUser());
        }
    }


    private Drive getDriveApi(Credential credential)
    {
        log.debug("Initiating Google Drive Connection");
        return new Drive.Builder(new NetHttpTransport(), new JacksonFactory(), null).setHttpRequestInitializer(credential).setApplicationName(GoogleDocsConstants.APPLICATION_NAME).build();
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
            try
            {
                getCredential();
                authenticated = true;
            }
            catch (Exception e)
            {
                authenticated = false;
            }
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
            throws IOException
    {
        String authenticateUrl = null;

        if (state != null)
        {
            GoogleAuthorizationCodeRequestUrl urlBuilder = null;

            if (StringUtils.isBlank(getGoogleUserName()))
            {
                urlBuilder = getFlow().newAuthorizationUrl().setRedirectUri(GoogleDocsConstants.REDIRECT_URI).setState(state);
            }
            else
            {
                urlBuilder = getFlow().newAuthorizationUrl().setRedirectUri(GoogleDocsConstants.REDIRECT_URI).setState(state).set("login_hint", getGoogleUserName());
            }

            authenticateUrl = urlBuilder.build();
        }

        log.debug("Authentication URL: " + authenticateUrl);
        return authenticateUrl;
    }


    private String getGoogleUserName()
    {
        NodeRef person = personService.getPerson(AuthenticationUtil.getRunAsUser());

        return (String)nodeService.getProperty(person, ContentModel.PROP_GOOGLEUSERNAME);
    }


    public boolean completeAuthentication(String authorizationCode)
            throws GoogleDocsServiceException,
            IOException
    {
        boolean authenticationComplete = false;

        GoogleTokenResponse response = getFlow().newTokenRequest(authorizationCode).setRedirectUri(GoogleDocsConstants.REDIRECT_URI).execute();

        try
        {
            // If this is a reauth....we may not get back the refresh token. We
            // need to make sure it is persisted across the "refresh".
            if (response.getRefreshToken() == null)
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
                        response.setRefreshToken(credentialInfo.getOAuthRefreshToken());

                        log.debug("Persisting Refresh Token across reauth");
                    }
                }
            }

            oauth2CredentialsStoreService.storePersonalOAuth2Credentials(GoogleDocsConstants.REMOTE_SYSTEM, response.getAccessToken(), response.getRefreshToken(), new Date(response.getExpiresInSeconds()), new Date());

            authenticationComplete = true;
        }
        catch (NoSuchSystemException nsse)
        {
            throw new GoogleDocsServiceException(nsse.getMessage());
        }

        log.debug("Authentication Complete: " + authenticationComplete);

        return authenticationComplete;
    }


    /*
     * (non-Javadoc)
     * 
     * @see
     * org.alfresco.integrations.google.docs.service.GoogleDocsService#createDocument(org.alfresco.service.cmr.repository.NodeRef)
     */
    public File createDocument(Credential credential, NodeRef nodeRef)
            throws GoogleDocsServiceException,
            GoogleDocsTypeException,
            GoogleDocsAuthenticationException,
            GoogleDocsRefreshTokenException,
            IOException
    {
        //Get the users drive credential if not provided;
        credential = credential == null ? getCredential() : credential;

        Drive drive = getDriveApi(credential);
        log.debug("Create Google Document for node " + nodeRef);

        File file = null;
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
            File workingDir = createWorkingDirectory(credential, nodeRef);

            // Create the Google Document in the working directory
            file = new File().setParents(Arrays.asList(new ParentReference().setId(workingDir.getId()))).setTitle(name).setMimeType(mimetype);
            file = drive.files().insert(file).execute();

            // Add temporary Node (with Content) to repository.
            ContentWriter writer = fileFolderService.getWriter(nodeRef);
            writer.setMimetype(MimetypeMap.MIMETYPE_OPENXML_WORDPROCESSING);
            writer.putContent(newDocument.getInputStream());

        }
        catch (IOException ioe)
        {
            throw ioe;
        }

        return file;
    }


    /*
     * (non-Javadoc)
     * 
     * @see
     * org.alfresco.integrations.google.docs.service.GoogleDocsService#createSpreadSheet(org.alfresco.service.cmr.repository.NodeRef
     * )
     */
    public File createSpreadSheet(Credential credential, NodeRef nodeRef)
            throws GoogleDocsServiceException,
            GoogleDocsTypeException,
            GoogleDocsAuthenticationException,
            GoogleDocsRefreshTokenException,
            IOException
    {
        //Get the users drive credential if not provided;
        credential = credential == null ? getCredential() : credential;

        Drive drive = getDriveApi(credential);

        log.debug("Create Google Spreadsheet for node " + nodeRef);

        File file = null;
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
            File workingDir = createWorkingDirectory(credential, nodeRef);

            // Create the Google Spreadsheet in the working directory
            file = new File().setParents(Arrays.asList(new ParentReference().setId(workingDir.getId()))).setTitle(name).setMimeType(mimetype);
            file = drive.files().insert(file).execute();

            // Add temporary Node (with Content) to the repository
            ContentWriter writer = fileFolderService.getWriter(nodeRef);
            writer.setMimetype(MimetypeMap.MIMETYPE_OPENXML_SPREADSHEET);
            writer.putContent(newSpreadsheet.getInputStream());

        }
        catch (IOException ioe)
        {
            throw ioe;
        }

        return file;
    }


    /*
     * (non-Javadoc)
     * 
     * @see
     * org.alfresco.integrations.google.docs.service.GoogleDocsService#createPresentation(org.alfresco.service.cmr.repository.NodeRef
     * )
     */
    public File createPresentation(Credential credential, NodeRef nodeRef)
            throws GoogleDocsServiceException,
            GoogleDocsTypeException,
            GoogleDocsAuthenticationException,
            GoogleDocsRefreshTokenException,
            IOException
    {
        //Get the users drive credential if not provided;
        credential = credential == null ? getCredential() : credential;

        Drive drive = getDriveApi(credential);

        log.debug("Create Google Presentation for node " + nodeRef);

        File file = null;
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
            File workingDir = createWorkingDirectory(credential, nodeRef);

            // Create the Google Document in the working directory
            file = new File().setParents(Arrays.asList(new ParentReference().setId(workingDir.getId()))).setTitle(name).setMimeType(mimetype);
            file = drive.files().insert(file).execute();

            // Add temporary Node (with Content) to repository
            ContentWriter writer = fileFolderService.getWriter(nodeRef);
            writer.setMimetype(MimetypeMap.MIMETYPE_OPENXML_PRESENTATION);
            writer.putContent(newPresentation.getInputStream());

        }
        catch (IOException ioe)
        {
            throw ioe;
        }

        return file;
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
    private void getDocument(Credential credential, NodeRef nodeRef, String resourceID, boolean removeFromDrive)
            throws GoogleDocsAuthenticationException,
            GoogleDocsServiceException,
            IOException,
            GoogleDocsRefreshTokenException
    {
        log.debug("Get Google Document for node: " + nodeRef);
        //Get the users drive credential if not provided;
        credential = credential == null ? getCredential() : credential;
        Drive drive = getDriveApi(credential);

        try
        {
            String mimetype = null;

            mimetype = validateMimeType(fileFolderService.getFileInfo(nodeRef).getContentData().getMimetype());
            log.debug("Current mimetype: " + fileFolderService.getFileInfo(nodeRef).getContentData().getMimetype()
                      + "; Mimetype of Google Doc: " + mimetype);
            log.debug("Export format: " + mimetype);

            File file = drive.files().get(resourceID.substring(resourceID.lastIndexOf(':') + 1)).execute();

            InputStream inputstream = getFileInputStream(credential, file, mimetype);

            ContentWriter writer = fileFolderService.getWriter(nodeRef);
            writer.setMimetype(mimetype);
            writer.putContent(inputstream);

            renameNode(nodeRef, file.getTitle());

            saveSharedInfo(credential, nodeRef, resourceID.substring(resourceID.lastIndexOf(':') + 1));

            if (removeFromDrive)
            {
                deleteContent(credential, nodeRef, file);
            }
            else
            {
                nodeService.setProperty(nodeRef, GoogleDocsModel.PROP_REVISION_ID, getLatestRevision(credential, file).getId());
            }

            postActivity(nodeRef);

            if (nodeService.hasAspect(nodeRef, ContentModel.ASPECT_TEMPORARY))
            {
                nodeService.removeAspect(nodeRef, ContentModel.ASPECT_TEMPORARY);
                log.debug("Temporary Aspect Removed");
            }
        }
        catch (GoogleJsonResponseException e)
        {
            throw new GoogleDocsServiceException(e.getMessage(), e.getStatusCode(), e);
        }
        catch (JSONException jsonException)
        {
            throw new GoogleDocsAuthenticationException(
                    "Unable to create activity entry: " + jsonException.getMessage(), jsonException);
        }
    }


    /*
     * (non-Javadoc)
     * 
     * @see org.alfresco.integrations.google.docs.service.GoogleDocsService#getDocument(org.alfresco.service.cmr.repository.NodeRef)
     */
    public void getDocument(Credential credential, NodeRef nodeRef, boolean removeFromDrive)
            throws GoogleDocsAuthenticationException,
            GoogleDocsServiceException,
            IOException,
            GoogleDocsRefreshTokenException
    {
        //Get the users drive credential if not provided;
        credential = credential == null ? getCredential() : credential;

        String resourceID = nodeService.getProperty(nodeRef, GoogleDocsModel.PROP_RESOURCE_ID).toString();
        if (resourceID == null)
        {
            throw new NotInGoogleDriveException(nodeRef);
        }

        getDocument(credential, nodeRef, resourceID.substring(resourceID.lastIndexOf(':') + 1), removeFromDrive);
    }


    public void getDocument(Credential credential, NodeRef nodeRef)
            throws GoogleDocsAuthenticationException,
            GoogleDocsServiceException,
            IOException,
            GoogleDocsRefreshTokenException
    {
        //Get the users drive credential if not provided;
        credential = credential == null ? getCredential() : credential;

        String resourceID = nodeService.getProperty(nodeRef, GoogleDocsModel.PROP_RESOURCE_ID).toString();
        if (resourceID == null)
        {
            throw new NotInGoogleDriveException(nodeRef);
        }

        getDocument(credential, nodeRef, resourceID.substring(resourceID.lastIndexOf(':') + 1), false);
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
    private void getSpreadSheet(Credential credential, NodeRef nodeRef, String resourceID, boolean removeFromDrive)
            throws GoogleDocsAuthenticationException,
            GoogleDocsServiceException,
            GoogleDocsRefreshTokenException,
            IOException
    {
        log.debug("Get Google Spreadsheet for node: " + nodeRef);
        //Get the users drive credential if not provided;
        credential = credential == null ? getCredential() : credential;
        Drive drive = getDriveApi(credential);

        try
        {
            String mimetype = null;

            mimetype = validateMimeType(fileFolderService.getFileInfo(nodeRef).getContentData().getMimetype());
            log.debug("Current mimetype: " + fileFolderService.getFileInfo(nodeRef).getContentData().getMimetype()
                      + "; Mimetype of Google Doc: " + mimetype);
            log.debug("Export format: " + mimetype);

            File file = drive.files().get(resourceID.substring(resourceID.lastIndexOf(':') + 1)).execute();

            InputStream inputStream = getFileInputStream(credential, file, mimetype);

            ContentWriter writer = fileFolderService.getWriter(nodeRef);
            writer.setMimetype(mimetype);
            writer.putContent(inputStream);

            renameNode(nodeRef, file.getTitle());

            saveSharedInfo(credential, nodeRef, resourceID.substring(resourceID.lastIndexOf(':') + 1));

            if (removeFromDrive)
            {
                deleteContent(credential, nodeRef, file);
            }
            else
            {
                nodeService.setProperty(nodeRef, GoogleDocsModel.PROP_REVISION_ID, getLatestRevision(credential, file).getId());
            }

            postActivity(nodeRef);

            if (nodeService.hasAspect(nodeRef, ContentModel.ASPECT_TEMPORARY))
            {
                nodeService.removeAspect(nodeRef, ContentModel.ASPECT_TEMPORARY);
                log.debug("Temporary Aspect Removed");
            }
        }
        catch (GoogleJsonResponseException e)
        {
            throw new GoogleDocsServiceException(e.getMessage(), e.getStatusCode(), e);
        }
        catch (JSONException jsonException)
        {
            throw new GoogleDocsAuthenticationException(
                    "Unable to create activity entry: " + jsonException.getMessage(), jsonException);
        }
    }


    public void getSpreadSheet(Credential credential, NodeRef nodeRef, boolean removeFromDrive)
            throws GoogleDocsAuthenticationException,
            GoogleDocsServiceException,
            GoogleDocsRefreshTokenException,
            IOException
    {
        //Get the users drive credential if not provided;
        credential = credential == null ? getCredential() : credential;

        String resourceID = nodeService.getProperty(nodeRef, GoogleDocsModel.PROP_RESOURCE_ID).toString();
        if (resourceID == null)
        {
            throw new NotInGoogleDriveException(nodeRef);
        }

        getSpreadSheet(credential, nodeRef, resourceID.substring(resourceID.lastIndexOf(':') + 1), removeFromDrive);
    }


    /*
     * (non-Javadoc)
     * 
     * @see
     * org.alfresco.integrations.google.docs.service.GoogleDocsService#getSpreadSheet(org.alfresco.service.cmr.repository.NodeRef)
     */
    public void getSpreadSheet(Credential credential, NodeRef nodeRef)
            throws GoogleDocsAuthenticationException,
            GoogleDocsServiceException,
            IOException,
            GoogleDocsRefreshTokenException
    {
        //Get the users drive credential if not provided;
        credential = credential == null ? getCredential() : credential;

        String resourceID = nodeService.getProperty(nodeRef, GoogleDocsModel.PROP_RESOURCE_ID).toString();
        if (resourceID == null)
        {
            throw new NotInGoogleDriveException(nodeRef);
        }

        getSpreadSheet(credential, nodeRef, resourceID.substring(resourceID.lastIndexOf(':') + 1), false);
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
    private void getPresentation(Credential credential, NodeRef nodeRef, String resourceID, boolean removeFromDrive)
            throws GoogleDocsAuthenticationException,
            GoogleDocsServiceException,
            GoogleDocsRefreshTokenException,
            IOException
    {
        log.debug("Get Google Presentation for node: " + nodeRef);
        //Get the users drive credential if not provided;
        credential = credential == null ? getCredential() : credential;
        Drive drive = getDriveApi(credential);

        try
        {
            String mimetype = null;

            mimetype = validateMimeType(fileFolderService.getFileInfo(nodeRef).getContentData().getMimetype());
            log.debug("Current mimetype: " + fileFolderService.getFileInfo(nodeRef).getContentData().getMimetype()
                      + "; Mimetype of Google Doc: " + mimetype);
            log.debug("Export format: " + mimetype);

            File file = drive.files().get(resourceID.substring(resourceID.lastIndexOf(':') + 1)).execute();

            InputStream inputStream = getFileInputStream(credential, file, mimetype);

            ContentWriter writer = fileFolderService.getWriter(nodeRef);
            writer.setMimetype(mimetype);
            writer.putContent(inputStream);

            renameNode(nodeRef, file.getTitle());

            saveSharedInfo(credential, nodeRef, resourceID.substring(resourceID.lastIndexOf(':') + 1));

            if (removeFromDrive)
            {
                deleteContent(credential, nodeRef, file);
            }
            else
            {
                nodeService.setProperty(nodeRef, GoogleDocsModel.PROP_REVISION_ID, getLatestRevision(credential, file).getId());
            }

            postActivity(nodeRef);

            if (nodeService.hasAspect(nodeRef, ContentModel.ASPECT_TEMPORARY))
            {
                nodeService.removeAspect(nodeRef, ContentModel.ASPECT_TEMPORARY);
                log.debug("Temporary Aspect Removed");
            }
        }
        catch (GoogleJsonResponseException e)
        {
            throw new GoogleDocsServiceException(e.getMessage(), e.getStatusCode(),e);
        }
        catch (JSONException jsonException)
        {
            throw new GoogleDocsAuthenticationException(
                    "Unable to create activity entry: " + jsonException.getMessage(), jsonException);
        }
    }


    public void getPresentation(Credential credential, NodeRef nodeRef, boolean removeFromDrive)
            throws GoogleDocsAuthenticationException,
            GoogleDocsServiceException,
            IOException,
            GoogleDocsRefreshTokenException
    {
        //Get the users drive credential if not provided;
        credential = credential == null ? getCredential() : credential;

        String resourceID = nodeService.getProperty(nodeRef, GoogleDocsModel.PROP_RESOURCE_ID).toString();
        if (resourceID == null)
        {
            throw new NotInGoogleDriveException(nodeRef);
        }

        getPresentation(credential, nodeRef, resourceID.substring(resourceID.lastIndexOf(':') + 1), removeFromDrive);
    }


    /*
     * (non-Javadoc)
     * 
     * @see
     * org.alfresco.integrations.google.docs.service.GoogleDocsService#getPresentation(org.alfresco.service.cmr.repository.NodeRef)
     */
    public void getPresentation(Credential credential, NodeRef nodeRef)
            throws GoogleDocsAuthenticationException,
            GoogleDocsServiceException,
            IOException,
            GoogleDocsRefreshTokenException
    {
        //Get the users drive credential if not provided;
        credential = credential == null ? getCredential() : credential;

        String resourceID = nodeService.getProperty(nodeRef, GoogleDocsModel.PROP_RESOURCE_ID).toString();
        if (resourceID == null)
        {
            throw new NotInGoogleDriveException(nodeRef);
        }

        getPresentation(credential, nodeRef, resourceID.substring(resourceID.lastIndexOf(':') + 1), false);
    }


    /*
     * (non-Javadoc)
     * 
     * @see org.alfresco.integrations.google.docs.service.GoogleDocsService#uploadFile(org.alfresco.service.cmr.repository.NodeRef)
     */
    public File uploadFile(Credential credential, NodeRef nodeRef)
            throws GoogleDocsAuthenticationException,
            GoogleDocsServiceException,
            GoogleDocsRefreshTokenException,
            IOException
    {
        log.debug("Upload " + nodeRef + " to Google");
        //Get the users drive credential if not provided;
        credential = credential == null ? getCredential() : credential;
        Drive drive = getDriveApi(credential);

        File file = null;

        // It makes me want to cry that they don't support inputStreams.
        java.io.File f = null;

        try
        {
            // Get the reader
            ContentReader reader = fileFolderService.getReader(nodeRef);

            f = java.io.File.createTempFile(nodeRef.getId(), ".tmp", TempFileProvider.getTempDir());
            reader.getContent(f);

            // Get the mimetype
            FileInfo fileInfo = fileFolderService.getFileInfo(nodeRef);
            String mimetype = fileInfo.getContentData().getMimetype();

            // Create the working Directory
            File workingDir = createWorkingDirectory(credential, nodeRef);

            file = new File().setParents(Arrays.asList(new ParentReference().setId(workingDir.getId()))).setTitle(fileInfo.getName()).setMimeType(mimetype);

            FileContent fileContent = new FileContent(mimetype, f);
            file = drive.files().insert(file, fileContent).setConvert(true).execute();
        }
        catch (IOException ioe)
        {
            throw ioe;
        }
        catch (Exception e)
        {
            throw new GoogleDocsServiceException(e.getMessage(), HttpStatus.SC_INTERNAL_SERVER_ERROR, e);
        }
        finally
        {
            if (f != null)
            {
                boolean success = f.delete();

                if (!success)
                {
                    log.debug("The temporary file used to upload to Google Drive was not successfully removed.");
                }
            }
        }

        return file;
    }


    /**
     * Unlock and Undecorate node; Remove content from users Google Account Does not update the content in Alfresco; If content was
     * newly created by GoogleDocsService it will be removed.
     * <p/>
     * Method can be run by owner, admin or site manager
     *
     * @param nodeRef
     * @param file
     * @param forceRemoval ignore <code>GoogleDocsServiceException</code> exceptions when attempting to remove content from user's
     *                     Google account
     * @throws GoogleDocsRefreshTokenException
     * @throws GoogleDocsServiceException
     * @throws GoogleDocsAuthenticationException
     */
    public void removeContent(Credential credential, NodeRef nodeRef, File file, boolean forceRemoval)
            throws GoogleDocsAuthenticationException,
            GoogleDocsServiceException,
            GoogleDocsRefreshTokenException,
            IOException
    {
        if (isGoogleDocsLockOwner(nodeRef) || authorityService.hasAdminAuthority()
            || isSiteManager(nodeRef, AuthenticationUtil.getFullyAuthenticatedUser()))
        {
            //Get the users drive credential if not provided;
            credential = credential == null ? getCredential() : credential;

            unlockNode(nodeRef);

            try
            {
                deleteContent(credential, nodeRef, file); // also undecorates node
            }
            catch (GoogleDocsServiceException gdse)
            {
                if (forceRemoval)
                {
                    log.debug("There was an error (" + gdse.getMessage() + ": " + gdse.getPassedStatusCode() + ") removing "
                              + file.getTitle() + " from " + AuthenticationUtil.getFullyAuthenticatedUser()
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
    public boolean isSiteManager(NodeRef nodeRef, String authorityName)
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
    public boolean deleteContent(Credential credential, NodeRef nodeRef, File file)
            throws GoogleDocsAuthenticationException,
            GoogleDocsServiceException,
            GoogleDocsRefreshTokenException,
            IOException
    {
        log.debug("Delete Google Doc for " + nodeRef);
        //Get the users drive credential if not provided;
        credential = credential == null ? getCredential() : credential;

        Drive drive = getDriveApi(credential);

        boolean deleted = false;

        try
        {
            if (file != null)
            {
                drive.files().delete(file.getId()).execute();

                // Delete the Working directory in Google Drive (if it exists....this should handle any migration issues)
                deleteWorkingDirectory(credential, nodeRef);
            }

            unDecorateNode(nodeRef);
            deleted = true;
        }
        catch (GoogleJsonResponseException e)
        {
            throw new GoogleDocsServiceException(e.getMessage(), e.getStatusCode(), e);
        }

        log.debug("Deleted: " + deleted);
        return deleted;
    }

    public boolean deleteContent(Credential credential, NodeRef nodeRef)
            throws GoogleDocsAuthenticationException,
            GoogleDocsServiceException,
            GoogleDocsRefreshTokenException,
            IOException
    {
        //Get the users drive credential if not provided;
        credential = credential == null ? getCredential() : credential;

        File file = getDriveFile(credential, nodeRef);

        return deleteContent(credential, nodeRef, file);
    }


    /*
     * (non-Javadoc)
     * 
     * @see
     * org.alfresco.integrations.google.docs.service.GoogleDocsService#getLatestRevision(org.alfresco.service.cmr.repository.NodeRef
     * )
     */
    public Revision getLatestRevision(Credential credential, NodeRef nodeRef)
            throws GoogleDocsAuthenticationException,
            GoogleDocsRefreshTokenException,
            GoogleDocsServiceException,
            IOException
    {
        //Get the users drive credential if not provided;
        credential = credential == null ? getCredential() : credential;

        Revision revision = null;

        try
        {
            if (nodeService.getProperty(nodeRef, GoogleDocsModel.PROP_RESOURCE_ID) != null)
            {
                File file = new File().setId(nodeService.getProperty(nodeRef, GoogleDocsModel.PROP_RESOURCE_ID).toString());

                revision = getLatestRevision(credential, file);
            }
        }
        catch (GoogleJsonResponseException e)
        {
            throw new GoogleDocsServiceException(e.getMessage(), e.getStatusCode(), e);
        }

        return revision;
    }


    public Revision getLatestRevision(Credential credential, File file)
            throws GoogleDocsAuthenticationException,
            GoogleDocsRefreshTokenException,
            GoogleDocsServiceException,
            IOException
    {
        //Get the users drive credential if not provided;
        credential = credential == null ? getCredential() : credential;
        Drive drive = getDriveApi(credential);

        Revision revision = null;

        try
        {
            RevisionList revisionList = drive.revisions().list(file.getId()).execute();
            List<Revision> fileRevisions = revisionList.getItems();

            if (fileRevisions != null)
            {
                Collections.sort(fileRevisions, new FileRevisionComparator());

                revision = fileRevisions.get(fileRevisions.size() - 1);
            }
        }
        catch (GoogleJsonResponseException e)
        {
            throw new GoogleDocsServiceException(e.getMessage(), e.getStatusCode(), e);
        }

        return revision;
    }


    /*
     * (non-Javadoc)
     * 
     * @see
     * org.alfresco.integrations.google.docs.service.GoogleDocsService#decorateNode(org.alfresco.service.cmr.repository.NodeRef,
     * org.springframework.social.google.api.drive.DriveFile, boolean)
     */
    public void decorateNode(NodeRef nodeRef, File file, boolean newcontent)
    {
        decorateNode(nodeRef, file, null, null, newcontent);
    }


    /*
     * (non-Javadoc)
     * 
     * @see
     * org.alfresco.integrations.google.docs.service.GoogleDocsService#decorateNode(org.alfresco.service.cmr.repository.NodeRef,
     * org.springframework.social.google.api.drive.DriveFile, org.springframework.social.google.api.drive.FileRevision, boolean)
     */
    public void decorateNode(NodeRef nodeRef, File file, Revision revision, boolean newcontent)
    {
        decorateNode(nodeRef, file, revision, null, newcontent);
    }


    /*
     * (non-Javadoc)
     * 
     * @see
     * org.alfresco.integrations.google.docs.service.GoogleDocsService#decorateNode(org.alfresco.service.cmr.repository.NodeRef,
     * org.springframework.social.google.api.drive.DriveFile, org.springframework.social.google.api.drive.FileRevision,
     * java.util.List<org.alfresco.integrations.google.docs.service.GoogleDocsService.GooglePermission>, boolean)
     */
    public void decorateNode(NodeRef nodeRef, File file, Revision revision, List<GooglePermission> permissions, boolean newcontent)
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
            aspectProperties.put(GoogleDocsModel.PROP_RESOURCE_ID, file.getId());
            aspectProperties.put(GoogleDocsModel.PROP_EDITORURL, file.getAlternateLink());
            aspectProperties.put(GoogleDocsModel.PROP_DRIVE_WORKING_FOLDER, file.getParents().get(0).getId());
            if (revision != null)
            {
                aspectProperties.put(GoogleDocsModel.PROP_REVISION_ID, revision.getId());
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
                      + ((revision != null) ? aspectProperties.get(GoogleDocsModel.PROP_REVISION_ID)
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
            new Boolean("false").equals(nodeService.getProperty(nodeRef, GoogleDocsModel.PROP_LOCKED)))
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
     * @param nodeRef
     * @return Return the Google Docs Lock Owner
     */
    public String getGoogleDocsLockOwner(NodeRef nodeRef)
    {
        String lockOwner = null;

        if(isLockedByGoogleDocs(nodeRef))
        {
            LockState lockState = lockservice.getLockState(nodeRef);

            lockOwner = lockState.getOwner();
        }

        return lockOwner;
    }


    /**
     * Find nodes using duplicate name in same context (folder/space).
     *
     * @param nodeRef
     * @param name    if null, name will be pulled from nodeRef
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
     * @param office2007extension
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
     * @param name    New name
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
        else
        {
            String guessedMimetype = mimetypeService.guessMimetype(name);
            if (guessedMimetype == null || !mimetype.equals(guessedMimetype))
            {
                String oldName = name, mimeTypeExtension = mimetypeService.getExtension(mimetype);
                name = name.concat("." + mimeTypeExtension);
                if (log.isInfoEnabled())
                {
                    log.info("Rename file '" + oldName + "' to '" + name + "'");
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


    public boolean hasConcurrentEditors(Credential credential, NodeRef nodeRef)
            throws GoogleDocsAuthenticationException,
            GoogleDocsRefreshTokenException,
            GoogleDocsServiceException,
            IOException
    {
        log.debug("Check for Concurrent Editors (Edits that have occured in the last " + idleThreshold + " seconds)");
        //Get the users drive credential if not provided;
        credential = credential == null ? getCredential() : credential;
        Drive drive = getDriveApi(credential);

        boolean concurrentChange = false;

        if (nodeService.hasAspect(nodeRef, GoogleDocsModel.ASPECT_EDITING_IN_GOOGLE))
        {
            String resourceID = nodeService.getProperty(nodeRef, GoogleDocsModel.PROP_RESOURCE_ID).toString();

            try
            {
                RevisionList revisionList = drive.revisions().list(resourceID.substring(resourceID.lastIndexOf(':') + 1)).execute();
                List<Revision> revisions = revisionList.getItems();

                if (revisions.size() > 1)
                {
                    log.debug("Revisions Found");
                    Collections.sort(revisions, Collections.reverseOrder(new FileRevisionComparator()));

                    // Find any revisions occurring within the last 'idleThreshold'
                    // seconds
                    List<Revision> workingList = new ArrayList<Revision>();

                    Calendar bufferTime = Calendar.getInstance();
                    bufferTime.add(Calendar.SECOND, -idleThreshold);

                    for (Revision entry : revisions)
                    {
                        Date d = new Date(entry.getModifiedDate().getValue());
                        if (d.after(new Date(bufferTime.getTimeInMillis())))
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
                            Revision revision = workingList.get(i);
                            String emailAddress = getDriveUser(credential).getEmailAddress();

                            // if there is no author -- the entry is the initial
                            // creation
                            if (revision.getLastModifyingUser().getEmailAddress() != null)
                            {
                                if (revision.getLastModifyingUser().getEmailAddress().equals(emailAddress))
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
                    String emailAddress = getDriveUser(credential).getEmailAddress();

                    // if the authors list is empty -- the author was the original
                    // creator and it is the initial copy
                    if (revisions.get(0).getLastModifyingUser().getEmailAddress() != null)
                    {
                        if (!revisions.get(0).getLastModifyingUser().getEmailAddress().equals(emailAddress))
                        {
                            Calendar bufferTime = Calendar.getInstance();
                            bufferTime.add(Calendar.SECOND, -idleThreshold);

                            Date dt = new Date(revisions.get(0).getModifiedDate().getValue());
                            if (dt.before(new Date(bufferTime.getTimeInMillis())))
                            {
                                log.debug("Revisions not made by current user found.");
                                concurrentChange = true;
                            }
                        }
                    }
                }

            }
            catch (GoogleJsonResponseException e)
            {
                //GOOGLEDOC-326 - need to handle case where 500 is returned but it actually maybe (or should be) a 404
                if (HttpStatus.SC_INTERNAL_SERVER_ERROR == e.getStatusCode())
                {
                    File file = getDriveFile(credential, nodeRef);

                    if (file == null)
                    {
                        throw new GoogleDocsServiceException("Unable to retrived Revisions. The file can no longer be found in Drive.", HttpStatus.SC_NOT_FOUND, e);
                    }
                }

                throw new GoogleDocsServiceException(e.getMessage(), e.getStatusCode(), e);
            }
        }
        else
        {
            throw new AspectMissingException(GoogleDocsModel.ASPECT_EDITING_IN_GOOGLE, nodeRef);
        }

        log.debug("Concurrent Edits: " + concurrentChange);
        return concurrentChange;
    }


    public File getDriveFile(Credential credential, String resourceID)
            throws GoogleDocsServiceException,
            GoogleDocsAuthenticationException,
            GoogleDocsRefreshTokenException,
            IOException
    {
        log.debug("Get Document list entry for resource " + resourceID.substring(resourceID.lastIndexOf(':') + 1));

        //Get the users drive credential if not provided;
        credential = credential == null ? getCredential() : credential;
        Drive drive = getDriveApi(credential);

        File file = null;

        try
        {
            file = drive.files().get(resourceID.substring(resourceID.lastIndexOf(':') + 1)).execute();
        }
        catch (GoogleJsonResponseException e)
        {
            file = null;
        }

        return file;
    }


    public File getDriveFile(Credential credential, NodeRef nodeRef)
            throws GoogleDocsServiceException,
            GoogleDocsAuthenticationException,
            GoogleDocsRefreshTokenException,
            IOException
    {
        //Get the users drive credential if not provided;
        credential = credential == null ? getCredential() : credential;

        String resourceID = nodeService.getProperty(nodeRef, GoogleDocsModel.PROP_RESOURCE_ID).toString();
        log.debug("Node " + nodeRef + " maps to Resource ID " + resourceID.substring(resourceID.lastIndexOf(':') + 1));

        if (resourceID == null)
        {
            throw new NotInGoogleDriveException(nodeRef);
        }

        return getDriveFile(credential, resourceID.substring(resourceID.lastIndexOf(':') + 1));
    }


    private String getExportLink(File file, String mimetype)
    {
        Map<String, String> exportLinks = file.getExportLinks();

        return exportLinks.get(validateMimeType(mimetype));
    }


    private InputStream getFileInputStream(Credential credential, File file, String mimetype)
            throws GoogleDocsAuthenticationException,
            GoogleDocsRefreshTokenException,
            GoogleDocsServiceException,
            IOException
    {
        //Get the users drive credential if not provided;
        credential = credential == null ? getCredential() : credential;

        Drive drive = getDriveApi(credential);

        HttpResponse response;

        String url = getExportLink(file, mimetype);
        log.debug("Google Export Format (mimetype) link: " + url);

        if (url == null)
        {
            throw new GoogleDocsServiceException("Google Docs Export Format not found.", HttpStatus.SC_UNSUPPORTED_MEDIA_TYPE);
        }

        response = drive.getRequestFactory().buildGetRequest(new GenericUrl(url)).execute();

        return response.getContent();
    }


    public User getDriveUser(Credential credential)
            throws GoogleDocsAuthenticationException,
            GoogleDocsRefreshTokenException,
            GoogleDocsServiceException,
            IOException
    {
        log.debug("Get Google Docs user metadata");
        //Get the users drive credential if not provided;
        credential = credential == null ? getCredential() : credential;
        Drive drive = getDriveApi(credential);

        User user = null;

        try
        {
            About about = drive.about().get().execute();
            user = about.getUser();
        }
        catch (GoogleJsonResponseException e)
        {
            throw new GoogleDocsServiceException(e.getMessage(), e.getStatusCode(), e);
        }

        return user;
    }


    private void postActivity(NodeRef nodeRef)
            throws JSONException
    {
        log.debug("Create Activity Stream Entry");
        if (personService.personExists(AuthenticationUtil.getRunAsUser()))
        {
            try
            {
                SiteInfo siteInfo = null;
                String pathElement = getPathElement(nodeRef, 2);

                //Is the node in a site?
                if (pathElement.equals(GoogleDocsConstants.ALF_SITES_PATH_FQNS_ELEMENT))
                {
                    siteInfo = siteService.getSite(nodeRef);
                }

                //If this is not in a site following current behaviour we will not updated the activity stream
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
     * Retrieve the file's ACL from Google and return a list of users who are listed in the ACL along with their roles.
     * 
     * @param resourceId Identifier for the file on Google
     * @throws GoogleDocsAuthenticationException
     * @throws GoogleDocsRefreshTokenException
     * @throws GoogleDocsServiceException
     * @throws IOException
     * @return Map where each represents a username and the value the role name
     */
    private List<GooglePermission> getFilePermissions(Credential credential, String resourceId)
            throws GoogleDocsAuthenticationException,
            GoogleDocsRefreshTokenException,
            GoogleDocsServiceException,
            IOException
    {
        //Get the users drive credential if not provided;
        credential = credential == null ? getCredential() : credential;
        Drive drive = getDriveApi(credential);

        List<GooglePermission> permissionsMap = new ArrayList<GooglePermission>();

        try
        {
            if (log.isDebugEnabled())
            {
                log.error("Looking up Google user profile");
            }
            User user = getDriveUser(credential);
            log.debug("Fetching permissions for file with resource ID " + resourceId);

            PermissionList permissionList = drive.permissions().list(resourceId.substring(resourceId.lastIndexOf(':') + 1)).execute();

            for (Permission permission : permissionList.getItems())
            {
                String role = permission.getRole(), scope = permission.getEmailAddress();
                String type = permission.getType();

                if (GooglePermission.role.reader.toString().equals(role) && permission.getAdditionalRoles() != null
                    && permission.getAdditionalRoles().size() == 1)
                {
                    role = GooglePermission.role.commenter.toString();
                }

                if (GooglePermission.role.owner.toString().equals(role) && user.getEmailAddress().equals(scope))
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
                    permissionsMap.add(new GooglePermission(scope, type, role));
                }
            }
        }
        catch (IOException ioe)
        {
            throw ioe;
        }

        return permissionsMap;
    }


    /**
     * Look up information from Google that describes the current state of the document, and store this into the repository.
     * <p/>
     * <p>
     * It is intended that this should be called prior to deleting the document from Google, and allows the state to be re-applied
     * if the content is subsequently edited again in Google.
     * </p>
     * <p/>
     * <p>
     * At present this stores only information on which Google users were explicitly listed as collaborators on the document.
     * </p>
     *
     * @param nodeRef    Noderef identifying the file in the repository
     * @param resourceId Identifier for the file on Google
     * @throws GoogleDocsAuthenticationException
     * @throws GoogleDocsRefreshTokenException
     * @throws GoogleDocsServiceException
     * @throws IOException
     */
    private void saveSharedInfo(Credential credential, NodeRef nodeRef, String resourceId)
            throws GoogleDocsAuthenticationException,
            GoogleDocsRefreshTokenException,
            GoogleDocsServiceException,
            IOException
    {
        //Get the users drive credential if not provided;
        credential = credential == null ? getCredential() : credential;

        List<GooglePermission> permissionsMap = getFilePermissions(credential, resourceId.substring(resourceId.lastIndexOf(':') + 1));
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


    public void addRemotePermissions(Credential credential, File file, List<GooglePermission> permissions)
            throws GoogleDocsAuthenticationException,
            GoogleDocsServiceException,
            GoogleDocsRefreshTokenException,
            IOException
    {
        //Get the users drive credential if not provided;
        credential = credential == null ? getCredential() : credential;
        Drive drive = getDriveApi(credential);

        if (log.isDebugEnabled())
        {
            log.debug("Adding permissions on item " + file.getId() + " in Google");
        }

        for (GooglePermission p : permissions)
        {
            String roleName = p.getRoleName(), authorityType = p.getAuthorityType();
            String role;
            String type;
            List<String> additionalRoles = new ArrayList<String>();
            if (roleName.equals(GooglePermission.role.reader.toString()))
            {
                role = GooglePermission.role.reader.toString();
            }
            else if (roleName.equals(GooglePermission.role.writer.toString()))
            {
                role = GooglePermission.role.writer.toString();
            }
            else if (roleName.equals(GooglePermission.role.owner.toString()))
            {
                role = GooglePermission.role.owner.toString();
            }
            else if (roleName.equals(GooglePermission.role.commenter.toString()))
            {
                role = GooglePermission.role.reader.toString();
                additionalRoles.add(GooglePermission.role.commenter.toString());
            }
            else
            {
                throw new IllegalArgumentException("Bad permission role " + roleName);
            }

            if (authorityType.equals(GooglePermission.type.user.toString()))
            {
                type = GooglePermission.type.user.toString();
            }
            else if (authorityType.equals(GooglePermission.type.group.toString()))
            {
                type = GooglePermission.type.group.toString();
            }
            else if (authorityType.equals(GooglePermission.type.domain.toString()))
            {
                type = GooglePermission.type.domain.toString();
            }
            else if (authorityType.equals(GooglePermission.type.anyone.toString()))
            {
                type = GooglePermission.type.anyone.toString();
            }
            else
            {
                throw new IllegalArgumentException("Bad permission type " + authorityType);
            }
            if (log.isDebugEnabled())
            {
                log.debug("Adding permission " + role + " for " + type + " " + p.getAuthorityId() + "");
            }

            drive.permissions().insert(file.getId(), new Permission().setRole(role).setType(type).setAdditionalRoles(additionalRoles).setValue(p.getAuthorityId())).execute();
        }
    }


    private File createWorkingDirectory(Credential credential, NodeRef nodeRef)
            throws GoogleDocsAuthenticationException,
            GoogleDocsRefreshTokenException,
            GoogleDocsServiceException,
            IOException
    {
        //Get the users drive credential if not provided;
        credential = credential == null ? getCredential() : credential;

        File file = null;

        // Get or create the parent folder
        if (googleDriveFolderExists(credential, GoogleDocsConstants.ROOT_FOLDER_ID, GoogleDocsConstants.ALF_TEMP_FOLDER))
        {
            List<File> files = getFolder(credential, GoogleDocsConstants.ROOT_FOLDER_ID, GoogleDocsConstants.ALF_TEMP_FOLDER);

            // Look for our description if there is more than one file returned
            if (!files.isEmpty() && files.size() > 1)
            {
                for (File f : files)
                {
                    if (StringUtils.equals(f.getDescription(), GoogleDocsConstants.ALF_TEMP_FOLDER_DESC))
                    {
                        file = f;
                        break;
                    }
                }

                if (file == null)
                {
                    file = createFolder(credential, GoogleDocsConstants.ROOT_FOLDER_ID, GoogleDocsConstants.ALF_TEMP_FOLDER, GoogleDocsConstants.ALF_TEMP_FOLDER_DESC);
                }
            }
            else if (!files.isEmpty() && files.size() == 1)
            {
                file = files.get(0);
            }
        }
        else
        {
            file = createFolder(credential, GoogleDocsConstants.ROOT_FOLDER_ID, GoogleDocsConstants.ALF_TEMP_FOLDER, GoogleDocsConstants.ALF_TEMP_FOLDER_DESC);
        }

        // create working directory
        String folderName = null;
        String pathElement = getPathElement(nodeRef, 2);
        SiteInfo siteInfo = null;

        //Is the node located under a site?
        if (pathElement.equals(GoogleDocsConstants.ALF_SITES_PATH_FQNS_ELEMENT))
        {
            try
            {
                siteInfo = siteService.getSite(nodeRef);
                folderName = siteInfo.getShortName();
            }
            catch (org.alfresco.repo.security.permissions.AccessDeniedException e)
            {
                // When the user does not have permission to access the site node
                // We can't get the name of the site that the node is located in
                // So we can't place it in a site specific folder.
                // It will be placed in the root of the Working Directory
                log.debug("User does not have access to the containing sites info.  The document will be created in the root of the working directory. {" + nodeRef.toString() + "}");
            }
        }
        else
        {
            //is it in the shared folder path?
            if (pathElement.equals(GoogleDocsConstants.ALF_SHARED_PATH_FQNS_ELEMENT))
            {
                folderName = GoogleDocsConstants.ALF_SHARED_FILES_FOLDER;
            }
            else
            {
                //If it is not in a site or in Shared Files it is pulled My Files.
                folderName = GoogleDocsConstants.ALF_MY_FILES_FOLDER;
            }
        }

        //If the foldername is not set (GOOGLEDOCS-301) place it directly the working directory
        if (folderName != null)
        {
            file = createFolder(credential, file.getId(), folderName, null);
        }

        return file;
    }


    private String getPathElement(NodeRef nodeRef, int position)
    {
        Path path = nodeService.getPath(nodeRef);
        Path.Element element = path.get(position);

        return element.toString();
    }


    private void deleteWorkingDirectory(Credential credential, NodeRef nodeRef)
            throws GoogleDocsAuthenticationException,
            GoogleDocsRefreshTokenException,
            GoogleDocsServiceException,
            IOException
    {
        //Get the users drive credential if not provided;
        credential = credential == null ? getCredential() : credential;

        if (nodeService.getProperty(nodeRef, GoogleDocsModel.PROP_DRIVE_WORKING_FOLDER) != null
            && StringUtils.isNotBlank(nodeService.getProperty(nodeRef, GoogleDocsModel.PROP_DRIVE_WORKING_FOLDER).toString()))
        {
            String id = nodeService.getProperty(nodeRef, GoogleDocsModel.PROP_DRIVE_WORKING_FOLDER).toString();
            deleteFolder(credential, id);
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
    private boolean googleDriveFolderExists(Credential credential, String parentId, String folderName)
            throws GoogleDocsAuthenticationException,
            GoogleDocsRefreshTokenException,
            GoogleDocsServiceException,
            IOException
    {
        if (credential == null)
        {
            credential = getCredential();
        }

        boolean exists = false;

        List<File> files = getFolder(credential, parentId, folderName);

        if (!files.isEmpty())
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
    private File createFolder(Credential credential, String parentId, String folderName, String description)
            throws GoogleDocsServiceException,
            GoogleDocsAuthenticationException,
            GoogleDocsRefreshTokenException,
            IOException
    {
        //Get the users drive credential if not provided;
        credential = credential == null ? getCredential() : credential;

        //Set description to an empty string if not provided
        description = description == null ? "" : description;

        File file;
        Drive drive = getDriveApi(credential);

        try
        {
            file = new File().setMimeType(GoogleDocsConstants.FOLDER_MIMETYPE).setParents(Arrays.asList(new ParentReference().setId(parentId))).setTitle(folderName).setDescription(description);
            file = drive.files().insert(file).execute();
        }
        catch (GoogleJsonResponseException e)
        {
            throw new GoogleDocsServiceException(e.getMessage(), e.getStatusCode(), e);
        }

        return file;
    }


    private List<File> getFolder(Credential credential, String parentId, String folderName)
            throws GoogleDocsAuthenticationException,
            GoogleDocsRefreshTokenException,
            GoogleDocsServiceException,
            IOException
    {
        if (credential == null)
        {
            credential = getCredential();
        }

        List<File> files = new ArrayList<File>();
        FileList fileList = null;
        Drive drive = getDriveApi(credential);

        if (drive != null)
        {
            String query =
                    "title = '" + folderName + "' and mimeType = '" + GoogleDocsConstants.FOLDER_MIMETYPE + "' and '" + parentId
                    + "' in parents";
            log.debug("Get folder query string: " + query);
            Drive.Files.List request = drive.files().list().setQ(query);
            try
            {
                do
                {
                    if (fileList == null)
                    {
                        fileList = request.execute();
                    }
                    else
                    {
                        fileList = request.setPageToken(fileList.getNextPageToken()).execute();
                    }

                    List<File> childfolders = fileList.getItems();
                    if (childfolders != null && !childfolders.isEmpty())
                    {
                        files.addAll(childfolders);
                    }
                }
                while (fileList.getNextPageToken() != null && fileList.getNextPageToken().length() > 0);
            }
            catch (GoogleJsonResponseException e)
            {
                throw new GoogleDocsServiceException(e.getMessage(), e.getStatusCode(), e);
            }
        }

        return files;
    }


    /**
     * Delete Google Drive Folder
     *
     * @param folderId
     * @throws GoogleDocsAuthenticationException
     * @throws GoogleDocsRefreshTokenException
     * @throws GoogleDocsServiceException
     */
    private void deleteFolder(Credential credential, String folderId)
            throws GoogleDocsAuthenticationException,
            GoogleDocsRefreshTokenException,
            GoogleDocsServiceException,
            IOException
    {
        //Get the users drive credential if not provided;
        credential = credential == null ? getCredential() : credential;
        Drive drive = getDriveApi(credential);

        try
        {
            drive.files().delete(folderId).execute();
        }
        catch (GoogleJsonResponseException e)
        {
            if (HttpStatus.SC_NOT_FOUND == e.getStatusCode())
            {
                log.debug("Directory not found in Google Drive. This is not a fatal issue.");
            }
            else if(HttpStatus.SC_FORBIDDEN == e.getStatusCode())
            {
                if (e.getMessage().equals(GoogleDocsConstants.GOOGLE_ERROR_UNMUTABLE))
                {
                    log.debug("Unable to delete remote file. Google claims it is unmutable.");
                }
            }
            else
            {
                log.debug("Google has reported an issue deleting the folder.  This is not a fatal issue. " + e.getDetails());
            }
        }
    }
}
