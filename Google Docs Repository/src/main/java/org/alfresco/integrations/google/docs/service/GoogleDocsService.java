
package org.alfresco.integrations.google.docs.service;

import java.io.IOException;
import java.net.MalformedURLException;
import java.net.URL;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

import org.alfresco.integrations.AbstractIntegration;
import org.alfresco.integrations.google.docs.GoogleDocsConstants;
import org.alfresco.integrations.google.docs.exceptions.GoogleDocsAuthenticationException;
import org.alfresco.integrations.google.docs.exceptions.GoogleDocsServiceException;
import org.alfresco.integrations.google.docs.exceptions.GoogleDocsTypeException;
import org.alfresco.integrations.google.docs.exceptions.MustDowngradeFormatException;
import org.alfresco.integrations.google.docs.exceptions.MustUpgradeFormatException;
import org.alfresco.service.cmr.model.FileFolderService;
import org.alfresco.service.cmr.oauth2.OAuth2StoreService;
import org.alfresco.service.cmr.remotecredentials.OAuth2CredentialsInfo;
import org.alfresco.service.cmr.remoteticket.NoSuchSystemException;
import org.alfresco.service.cmr.repository.ContentWriter;
import org.alfresco.service.cmr.repository.NodeRef;
import org.apache.commons.httpclient.HttpStatus;
import org.springframework.social.ApiException;
import org.springframework.social.connect.Connection;
import org.springframework.social.google.docs.api.GoogleDocs;
import org.springframework.social.google.docs.connect.GoogleDocsConnectionFactory;
import org.springframework.social.oauth2.AccessGrant;
import org.springframework.social.oauth2.GrantType;
import org.springframework.social.oauth2.OAuth2Parameters;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;

import com.google.gdata.client.docs.DocsService;
import com.google.gdata.data.MediaContent;
import com.google.gdata.data.PlainTextConstruct;
import com.google.gdata.data.docs.DocumentEntry;
import com.google.gdata.data.docs.DocumentListEntry;
import com.google.gdata.data.docs.PresentationEntry;
import com.google.gdata.data.docs.SpreadsheetEntry;
import com.google.gdata.data.media.MediaSource;
import com.google.gdata.util.ServiceException;

public class GoogleDocsService extends AbstractIntegration implements GoogleDocsConstants
{
    //Services
    private OAuth2StoreService oauth2StoreService;
    private GoogleDocsConnectionFactory connectionFactory;
    private FileFolderService fileFolderService;

    //Property Mappings
    private Map<String, String> importFormats = new HashMap<String, String>();
    private Map<String, Set<String>> exportFormats = new HashMap<String, Set<String>>();
    private Map<String, String> upgradeMappings = new HashMap<String, String>();
    private Map<String, String> downgradeMappings = new HashMap<String, String>();

    public void setImportFormats(Map<String, String> importFormats)
    {
        this.importFormats = importFormats;
    }

    public void setExportFormats(Map<String, Set<String>> exportFormats)
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

    public void setOauth2StoreService(OAuth2StoreService oauth2StoreService)
    {
        this.oauth2StoreService = oauth2StoreService;
    }

    public void setConnectionFactory(GoogleDocsConnectionFactory connectionFactory)
    {
        this.connectionFactory = connectionFactory;
    }

    public void setFileFolderService(FileFolderService fileFolderService)
    {
        this.fileFolderService = fileFolderService;
    }

    public ArrayList<String> getImportFormatsList()
    {
        return new ArrayList<String>(importFormats.keySet());
    }

    public boolean isImportable(String mimetype)
    {
        return importFormats.containsKey(mimetype);
    }

    public String getImportType(String mimetype)
    {
        return importFormats.get(mimetype);
    }

    public boolean isExportable(String mimetype) throws MustUpgradeFormatException,
                MustDowngradeFormatException
    {
        if (isUpgrade(mimetype))
        {
            throw new MustUpgradeFormatException();
        }
        else if (isDownGrade(mimetype)) { throw new MustDowngradeFormatException(); }

        String type = getImportType(mimetype);
        Set<String> exportMimetypes = getExportableMimeTypes(type);

        return exportMimetypes.contains(mimetype);
    }

    public Set<String> getExportableMimeTypes(String type)
    {
        Set<String> mimetypes = new HashSet<String>();

        if (exportFormats.containsKey(type))
        {
            mimetypes = exportFormats.get(type);
        }

        return mimetypes;
    }

    public boolean isUpgrade(String mimetype)
    {
        return upgradeMappings.containsKey(mimetype);
    }

    public boolean isDownGrade(String mimetype)
    {
        return downgradeMappings.containsKey(mimetype);
    }

    private Connection<GoogleDocs> getConnection() throws GoogleDocsAuthenticationException
    {
        Connection<GoogleDocs> connection = null;

        OAuth2CredentialsInfo credentialInfo = oauth2StoreService
                    .getOAuth2Credentials(REMOTE_SYSTEM);

        if (credentialInfo != null)
        {

            AccessGrant accessGrant = new AccessGrant(credentialInfo.getOAuthAccessToken());

            try
            {
                connection = connectionFactory.createConnection(accessGrant);
            }
            catch (ApiException ae)
            {
                if (ae.getCause() instanceof ServiceException){
                    ServiceException se = (ServiceException) ae.getCause();
                    if (se.getHttpErrorCodeOverride() == HttpStatus.SC_UNAUTHORIZED){
                        accessGrant = refreshAccessToken();
                        connection = connectionFactory.createConnection(accessGrant);
                    }
                } 
            }
        }

        return connection;
    }

    public boolean isAuthenticated()
    {
        boolean authenticated = false;

        OAuth2CredentialsInfo credentialInfo = oauth2StoreService
                    .getOAuth2Credentials(REMOTE_SYSTEM);

        if (credentialInfo != null)
        {
            authenticated = true;
        }

        return authenticated;
    }

    public String getAuthenticateUrl(String state)
    {
        String authenticateUrl = null;

        if (state != null)
        {

            /*
             * When we change to spring social 1.0.2 OAuth2Parameters will need
             * to be updated OAuth2Parameters parameters = new
             * OAuth2Parameters(); parameters.setRedirectUri(REDIRECT_URI);
             * parameters.setScope(SCOPE); parameters.setState(state);
             */
            
            //TODO Add offline
            
            MultiValueMap<String, String> additionalParameters = new LinkedMultiValueMap<String, String>(1);
            additionalParameters.add("access_type", "offline");
            
            OAuth2Parameters parameters = new OAuth2Parameters(REDIRECT_URI, SCOPE, state, additionalParameters);
            parameters.getAdditionalParameters();
            authenticateUrl = connectionFactory.getOAuthOperations().buildAuthenticateUrl(
                        GrantType.AUTHORIZATION_CODE, parameters);

        }

        return authenticateUrl;
    }

    public boolean completeAuthentication(String access_token)
    {
        boolean authenticationComplete = false;

        AccessGrant accessGrant = connectionFactory.getOAuthOperations().exchangeForAccess(
                    access_token, REDIRECT_URI, null);

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
            oauth2StoreService.storeOAuth2Credentials(REMOTE_SYSTEM, accessGrant.getAccessToken(),
                        accessGrant.getRefreshToken(), expiresIn, new Date());

            authenticationComplete = true;
        }
        catch (NoSuchSystemException nsse)
        {
            throw new GoogleDocsAuthenticationException(nsse.getMessage());
        }

        return authenticationComplete;
    }
    
    private AccessGrant refreshAccessToken(){
        OAuth2CredentialsInfo credentialInfo = oauth2StoreService
                    .getOAuth2Credentials(REMOTE_SYSTEM);
        
        if (credentialInfo.getOAuthRefreshToken() != null){
            
            AccessGrant accessGrant = null;
            try
            {
                
                accessGrant = connectionFactory.getOAuthOperations().refreshAccess(credentialInfo.getOAuthRefreshToken(), null, null);
            }
            catch (ApiException ae)
            {
                if (ae.getCause() instanceof ServiceException){
                    ServiceException se = (ServiceException) ae.getCause();
                    if (se.getHttpErrorCodeOverride() == HttpStatus.SC_UNAUTHORIZED){
                        throw new GoogleDocsAuthenticationException("Token Refresh Failed.");
                    }
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
                    oauth2StoreService.storeOAuth2Credentials(REMOTE_SYSTEM, accessGrant.getAccessToken(),
                                credentialInfo.getOAuthRefreshToken(), expiresIn, new Date());
                }
                catch (NoSuchSystemException nsse)
                {
                    throw new GoogleDocsAuthenticationException(nsse.getMessage());
                } 
            }
            else
            {
                throw new GoogleDocsAuthenticationException("No Access Grant Returned.");
            } 
            
            return accessGrant;
            
        } else {
            throw new GoogleDocsAuthenticationException("No Refresh Token Provided");
        }
    }

    protected DocsService getDocsService(Connection<GoogleDocs> connection)
    {
        DocsService docsService = null;
        
        try {
            docsService = connection.getApi().setAuthentication(new DocsService(APPLICATION_NAME));
        } catch (ApiException error){
            System.out.println(error.getCause());
            System.out.println(error.getCause().getCause());
            System.out.println(error.getCause().getCause().getCause());
        }

        return docsService;

    }

    protected DocumentListEntry createContent(String type, String name)
                throws GoogleDocsServiceException, GoogleDocsTypeException
    {
        DocsService docsService = getDocsService(getConnection());

        DocumentListEntry entry = null;
        if (type.equals(DOCUMENT_TYPE))
        {
            if (name == null)
            {
                name = NEW_DOCUMENT_NAME;
            }
            entry = new DocumentEntry();
        }
        else if (type.equals(SPREADSHEET_TYPE))
        {
            if (name == null)
            {
                name = NEW_SPREADSHEET_NAME;
            }
            entry = new SpreadsheetEntry();
        }
        else if (type.equals(PRESENTATION_TYPE))
        {
            if (name == null)
            {
                name = NEW_PRESENTATION_NAME;
            }
            entry = new PresentationEntry();
        }
        

        if (entry != null)
        {
            entry.setTitle(new PlainTextConstruct(name));
            // TODO In production this should be set to true;
            entry.setHidden(false);
            entry.setContent(new PlainTextConstruct("     "));

            try
            {
                return docsService.insert(new URL(URL_CREATE_NEW_MEDIA), entry);
            }
            catch (MalformedURLException error)
            {
                throw new GoogleDocsServiceException(error.getMessage());
            }
            catch (IOException error)
            {
                throw new GoogleDocsServiceException(error.getMessage());
            }
            catch (ServiceException error)
            {
                throw new GoogleDocsServiceException(error.getMessage());
            }
        }
        else
        {
            throw new GoogleDocsTypeException("Type Unknown: " + type
                        + ". Must be document, spreadsheet, presentation or folder.");
        }
    }

    public DocumentListEntry createDocument(String name)
    {
        return createContent(DOCUMENT_TYPE, name);
    }

    public DocumentListEntry createSpreadSheet(String name)
    {
        return createContent(SPREADSHEET_TYPE, name);
    }

    public DocumentListEntry createPresentation(String name)
    {
        return createContent(PRESENTATION_TYPE, name);
    }

    public void getDocument(NodeRef nodeRef, String resourceID)
    {

        DocsService docsService = getDocsService(getConnection());

        try
        {
            MediaContent mc = new MediaContent();

            mc.setUri(URL_DOCUMENT_DOWNLOAD + "?docID="
                        + resourceID.substring(resourceID.lastIndexOf(':') + 1)
                        + "&exportFormat=doc");

            MediaSource ms = docsService.getMedia(mc);

            ContentWriter writer = fileFolderService.getWriter(nodeRef);
            writer.setMimetype("application/msword");
            writer.putContent(ms.getInputStream());

        }
        catch (IOException error)
        {
            throw new GoogleDocsServiceException(error.getMessage());
        }
        catch (ServiceException error)
        {
            throw new GoogleDocsServiceException(error.getMessage());
        }
    }

    public void getSpreadSheet(NodeRef nodeRef, String resourceID)
    {

        DocsService docsService = getDocsService(getConnection());

        try
        {
            MediaContent mc = new MediaContent();

            mc.setUri(URL_SPREADSHEET_DOWNLOAD + "?key="
                        + resourceID.substring(resourceID.lastIndexOf(':') + 1)
                        + "&exportFormat=xls");

            MediaSource ms = docsService.getMedia(mc);

            ContentWriter writer = fileFolderService.getWriter(nodeRef);
            writer.setMimetype("application/vnd.ms-excel");
            writer.putContent(ms.getInputStream());

        }
        catch (IOException error)
        {
            throw new GoogleDocsServiceException(error.getMessage());
        }
        catch (ServiceException error)
        {
            throw new GoogleDocsServiceException(error.getMessage());
        }
    }

    public void getPresentation(NodeRef nodeRef, String resourceID)
    {

        DocsService docsService = getDocsService(getConnection());

        try
        {
            MediaContent mc = new MediaContent();

            mc.setUri(URL_PRESENTATION_DOWNLOAD + "?docID="
                        + resourceID.substring(resourceID.lastIndexOf(':') + 1)
                        + "&exportFormat=ppt");

            MediaSource ms = docsService.getMedia(mc);

            ContentWriter writer = fileFolderService.getWriter(nodeRef);
            writer.setMimetype("application/vnd.ms-powerpoint");
            writer.putContent(ms.getInputStream());

        }
        catch (IOException error)
        {
            throw new GoogleDocsServiceException(error.getMessage());
        }
        catch (ServiceException error)
        {
            throw new GoogleDocsServiceException(error.getMessage());
        }
    }

    @Override
    public void removeApp(boolean removeContent)
    {
        // TODO Auto-generated method stub

    }

}
