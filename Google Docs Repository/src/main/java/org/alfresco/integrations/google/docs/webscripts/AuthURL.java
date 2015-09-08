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

package org.alfresco.integrations.google.docs.webscripts;


import java.io.IOException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.alfresco.integrations.google.docs.GoogleDocsModel;
import org.alfresco.integrations.google.docs.service.GoogleDocsService;
import org.alfresco.service.cmr.repository.NodeRef;
import org.apache.commons.httpclient.HttpStatus;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.springframework.extensions.webscripts.Cache;
import org.springframework.extensions.webscripts.Status;
import org.springframework.extensions.webscripts.WebScriptException;
import org.springframework.extensions.webscripts.WebScriptRequest;


/**
 * @author Jared Ottley <jared.ottley@alfresco.com>
 */
public class AuthURL
    extends GoogleDocsWebScripts
{
    private static final Log    log                 = LogFactory.getLog(AuthURL.class);

    private final static String MODEL_AUTHURL       = "authURL";
    private final static String MODEL_AUTHENTICATED = "authenticated";
    private final static String MODEL_PERMISSIONS   = "permissions";

    private final static String PARAM_STATE         = "state";
    private final static String PARAM_OVERRIDE      = "override";
    private final static String PARAM_NODEREF       = "nodeRef";

    private GoogleDocsService   googledocsService;


    public void setGoogledocsService(GoogleDocsService googledocsService)
    {
        this.googledocsService = googledocsService;
    }


    @Override
    protected Map<String, Object> executeImpl(WebScriptRequest req, Status status, Cache cache)
    {
        getGoogleDocsServiceSubsystem();

        String nodeRef = req.getParameter(PARAM_NODEREF);
        
        Map<String, Object> model = new HashMap<String, Object>();

        boolean authenticated = false;

        try
        {
            if (!Boolean.valueOf(req.getParameter(PARAM_OVERRIDE)))
            {
                if (googledocsService.isAuthenticated())
                {
                    authenticated = true;
                }
                else
                {
                    model.put(MODEL_AUTHURL, googledocsService.getAuthenticateUrl(req.getParameter(PARAM_STATE)));
                }

                log.debug("Authenticated: " + authenticated + "; AuthUrl: "
                          + ((model.containsKey(MODEL_AUTHURL)) ? model.get(MODEL_AUTHURL) : ""));
            }
            else
            {
                model.put(MODEL_AUTHURL, googledocsService.getAuthenticateUrl(req.getParameter(PARAM_STATE)));
                authenticated = googledocsService.isAuthenticated();
                log.debug("Forced AuthURL. AuthUrl: " + model.get(MODEL_AUTHURL) + "; Authenticated: " + authenticated);
            }

            if (nodeRef != null && nodeRef.length() > 0)
            {
                List<GoogleDocsService.GooglePermission> permissions =
                        googledocsService.getGooglePermissions(new NodeRef(nodeRef), GoogleDocsModel.PROP_PERMISSIONS);
                model.put(MODEL_PERMISSIONS, permissions); // permissions may be null
            }
        }
        catch(IOException ioe)
        {
            throw new WebScriptException(HttpStatus.SC_INTERNAL_SERVER_ERROR, ioe.getMessage());
        }

        model.put(MODEL_AUTHENTICATED, authenticated);

        return model;
    }
}
