
package org.alfresco.service.cmr.oauth2;

import java.util.Date;

import org.alfresco.service.Auditable;
import org.alfresco.service.cmr.remotecredentials.OAuth2CredentialsInfo;
import org.alfresco.service.cmr.remoteticket.NoSuchSystemException;

public interface OAuth2StoreService
{

    @Auditable(parameters = { "remoteSystemId" })
    public abstract OAuth2CredentialsInfo storeOAuth2Credentials(String remoteSystemId,
                String accessToken, String refreshToken, Date expiresAt, Date issuedAt)
                throws NoSuchSystemException;

    @Auditable(parameters = { "remoteSystemId" })
    public abstract OAuth2CredentialsInfo getOAuth2Credentials(String remoteSystemId)
                throws NoSuchSystemException;

    @Auditable(parameters = { "remoteSystemId" })
    public abstract boolean deleteOAuth2Credentials(String remoteSystemId)
                throws NoSuchSystemException;

}