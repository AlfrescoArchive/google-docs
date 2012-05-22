
package org.alfresco.repo.oauth2;

import java.util.Date;

import org.alfresco.repo.remotecredentials.OAuth2CredentialsInfoImpl;
import org.alfresco.service.cmr.oauth2.OAuth2StoreService;
import org.alfresco.service.cmr.remotecredentials.OAuth2CredentialsInfo;
import org.alfresco.service.cmr.remotecredentials.RemoteCredentialsService;
import org.alfresco.service.cmr.remoteticket.NoSuchSystemException;

public class OAuth2StoreServiceImpl implements OAuth2StoreService
{
    private RemoteCredentialsService remoteCredentialsService;

    public void setRemoteCredentialsService(RemoteCredentialsService remoteCredentialsService)
    {
        this.remoteCredentialsService = remoteCredentialsService;
    }

    /*
     * @see
     * org.alfresco.repo.oauth2.OAuth2StoreService#storeOAuth2Credentials(java
     * .lang.String, java.lang.String, java.lang.String, java.util.Date,
     * java.util.Date)
     */
    @Override
    public OAuth2CredentialsInfo storeOAuth2Credentials(String remoteSystemId, String accessToken,
                String refreshToken, Date expiresAt, Date issuedAt) throws NoSuchSystemException
    {

        OAuth2CredentialsInfoImpl credentials = new OAuth2CredentialsInfoImpl();

        OAuth2CredentialsInfoImpl existing = (OAuth2CredentialsInfoImpl) getOAuth2Credentials(remoteSystemId);
        if (existing != null)
        {
            credentials = existing;
        }

        credentials.setOauthAccessToken(accessToken);
        credentials.setOauthRefreshToken(refreshToken);
        credentials.setOauthTokenExpiresAt(expiresAt);
        if (issuedAt != null)
        {
            credentials.setOauthTokenIssuedAt(issuedAt);
        }
        else
        {
            credentials.setOauthTokenIssuedAt(new Date());
        }

        if (credentials.getNodeRef() != null)
        {
            return (OAuth2CredentialsInfo) remoteCredentialsService.updateCredentials(credentials);
        }
        else
        {
            return (OAuth2CredentialsInfo) remoteCredentialsService.createPersonCredentials(
                        remoteSystemId, credentials);
        }

    }

    /*
     * @see
     * org.alfresco.repo.oauth2.OAuth2StoreService#getOAuth2Credentials(java
     * .lang.String)
     */
    @Override
    public OAuth2CredentialsInfo getOAuth2Credentials(String remoteSystemId)
                throws NoSuchSystemException
    {
        return (OAuth2CredentialsInfo) remoteCredentialsService
                    .getPersonCredentials(remoteSystemId);
    }

    /*
     * @see
     * org.alfresco.repo.oauth2.OAuth2StoreService#deleteOAuth2Credentials(java
     * .lang.String)
     */
    @Override
    public boolean deleteOAuth2Credentials(String remoteSystemId) throws NoSuchSystemException
    {
        OAuth2CredentialsInfo credentials = getOAuth2Credentials(remoteSystemId);

        if (credentials == null) { return false; }

        remoteCredentialsService.deleteCredentials(credentials);

        return true;
    }
}
