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


import java.util.HashMap;
import java.util.Map;

import org.alfresco.integrations.google.docs.exceptions.GoogleDocsAuthenticationException;
import org.alfresco.integrations.google.docs.exceptions.GoogleDocsRefreshTokenException;
import org.alfresco.integrations.google.docs.exceptions.GoogleDocsServiceException;
import org.alfresco.integrations.google.docs.service.GoogleDocsService;
import org.apache.commons.httpclient.HttpStatus;
import org.springframework.extensions.webscripts.Cache;
import org.springframework.extensions.webscripts.DeclarativeWebScript;
import org.springframework.extensions.webscripts.Status;
import org.springframework.extensions.webscripts.WebScriptException;
import org.springframework.extensions.webscripts.WebScriptRequest;
import org.springframework.social.google.api.userinfo.GoogleUserProfile;


/**
 * @author Will Abson
 */
public class UserProfile extends DeclarativeWebScript
{
    private final static String MODEL_AUTHENTICATED = "authenticated";
    private final static String MODEL_EMAIL = "email";
    private final static String MODEL_NAME = "name";
    private final static String MODEL_FIRSTNAME = "firstName";
    private final static String MODEL_LASTNAME = "lastName";
    private final static String MODEL_ID = "id";

    private GoogleDocsService   googledocsService;


    public void setGoogledocsService(GoogleDocsService googledocsService)
    {
        this.googledocsService = googledocsService;
    }


    @Override
    protected Map<String, Object> executeImpl(WebScriptRequest req, Status status, Cache cache)
    {
        Map<String, Object> model = new HashMap<String, Object>();

        boolean authenticated = false;

        if (googledocsService.isAuthenticated())
        {
            authenticated = true;
            try
            {
                GoogleUserProfile profile = googledocsService.getGoogleUserProfile();
                model.put(MODEL_EMAIL, profile.getEmail());
                model.put(MODEL_NAME, profile.getName());
                model.put(MODEL_FIRSTNAME, profile.getFirstName());
                model.put(MODEL_LASTNAME, profile.getLastName());
                model.put(MODEL_ID, profile.getId());
            }
            catch (GoogleDocsAuthenticationException gdae)
            {
                throw new WebScriptException(HttpStatus.SC_BAD_GATEWAY, gdae.getMessage());
            }
            catch (GoogleDocsRefreshTokenException gdrte)
            {
                throw new WebScriptException(HttpStatus.SC_BAD_GATEWAY, gdrte.getMessage());
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
        }
        
        model.put(MODEL_AUTHENTICATED, authenticated);

        return model;
    }
}
