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

import org.alfresco.integrations.google.docs.exceptions.GoogleDocsServiceException;
import org.alfresco.integrations.google.docs.service.GoogleDocsService;
import org.apache.commons.httpclient.HttpStatus;
import org.springframework.extensions.webscripts.Cache;
import org.springframework.extensions.webscripts.Status;
import org.springframework.extensions.webscripts.WebScriptException;
import org.springframework.extensions.webscripts.WebScriptRequest;


/**
 * @author Jared Ottley <jared.ottley@alfresco.com>
 */
public class CompleteAuth
    extends GoogleDocsWebScripts
{
    private GoogleDocsService   googledocsService;

    private final static String PARAM_ACCESS_TOKEN  = "access_token";

    private final static String MODEL_AUTHENTICATED = "authenticated";


    public void setGoogledocsService(GoogleDocsService googledocsService)
    {
        this.googledocsService = googledocsService;
    }


    @Override
    protected Map<String, Object> executeImpl(WebScriptRequest req, Status status, Cache cache)
    {
        getGoogleDocsServiceSubsystem();

        Map<String, Object> model = new HashMap<String, Object>();

        boolean authenticated = false;

        if (req.getParameter(PARAM_ACCESS_TOKEN) != null)
        {
            try
            {
                authenticated = googledocsService.completeAuthentication(req.getParameter(PARAM_ACCESS_TOKEN));
            }
            catch (GoogleDocsServiceException gdse)
            {
                throw new WebScriptException(HttpStatus.SC_INTERNAL_SERVER_ERROR, gdse.getMessage());
            }
        }

        model.put(MODEL_AUTHENTICATED, authenticated);

        return model;
    }
}
