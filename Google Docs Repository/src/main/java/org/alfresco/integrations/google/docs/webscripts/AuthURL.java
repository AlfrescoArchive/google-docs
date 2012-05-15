
package org.alfresco.integrations.google.docs.webscripts;

import java.util.HashMap;
import java.util.Map;

import org.alfresco.integrations.google.docs.service.GoogleDocsService;
import org.springframework.extensions.webscripts.Cache;
import org.springframework.extensions.webscripts.DeclarativeWebScript;
import org.springframework.extensions.webscripts.Status;
import org.springframework.extensions.webscripts.WebScriptRequest;

public class AuthURL extends DeclarativeWebScript
{
    private final static String AUTHURL = "authURL";
    private final static String AUTHENTICATED = "authenticated";

    private GoogleDocsService googledocsService;

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
        }
        else
        {
            model.put(AUTHURL, googledocsService.getAuthenticateUrl(req.getParameter("state")));
            authenticated = false;
        }

        model.put(AUTHENTICATED, authenticated);

        return model;
    }

}
