
package org.alfresco.integrations.google.docs.service;

import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

import org.alfresco.integrations.AbstractIntegration;
import org.alfresco.integrations.google.docs.exceptions.GoogleDocsAuthenticationException;
import org.alfresco.integrations.google.docs.exceptions.MustDowngradeFormatException;
import org.alfresco.integrations.google.docs.exceptions.MustUpgradeFormatException;
import org.alfresco.service.cmr.oauth2.OAuth2StoreService;
import org.alfresco.service.cmr.remotecredentials.OAuth2CredentialsInfo;
import org.alfresco.service.cmr.remoteticket.NoSuchSystemException;
import org.apache.commons.httpclient.HttpStatus;
import org.springframework.social.connect.Connection;
import org.springframework.social.google.docs.api.GoogleDocs;
import org.springframework.social.google.docs.connect.GoogleDocsConnectionFactory;
import org.springframework.social.oauth2.AccessGrant;
import org.springframework.social.oauth2.GrantType;
import org.springframework.social.oauth2.OAuth2Parameters;
import org.springframework.web.client.HttpClientErrorException;

public class GoogleDocsService extends AbstractIntegration
{
    private OAuth2StoreService oauth2StoreService;
    private GoogleDocsConnectionFactory connectionFactory;

    private Map<String, String> importFormats = new HashMap<String, String>();
    private Map<String, Set<String>> exportFormats = new HashMap<String, Set<String>>();
    private Map<String, String> upgradeMappings = new HashMap<String, String>();
    private Map<String, String> downgradeMappings = new HashMap<String, String>();

    private static final String REMOTE_SYSTEM = "googledocs";
    private static final String REDIRECT_URI = "http://www.ottleys.net/test.html"; // "http://www.alfresco.com";
    private static final String SCOPE = "https://docs.google.com/feeds/ https://docs.googleusercontent.com/ https://spreadsheets.google.com/feeds/";

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
            catch (HttpClientErrorException hcee)
            {
                if (hcee.getStatusCode().ordinal() == HttpStatus.SC_FORBIDDEN) { throw new GoogleDocsAuthenticationException(); }
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

            OAuth2Parameters parameters = new OAuth2Parameters(REDIRECT_URI, SCOPE, state, null);

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

    @Override
    public void removeApp(boolean removeContent)
    {
        // TODO Auto-generated method stub

    }

}
