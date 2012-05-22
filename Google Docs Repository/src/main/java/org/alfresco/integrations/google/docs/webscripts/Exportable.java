
package org.alfresco.integrations.google.docs.webscripts;

import java.util.HashMap;
import java.util.Map;

import org.alfresco.integrations.google.docs.exceptions.MustDowngradeFormatException;
import org.alfresco.integrations.google.docs.exceptions.MustUpgradeFormatException;
import org.alfresco.integrations.google.docs.service.GoogleDocsService;
import org.apache.commons.httpclient.HttpStatus;
import org.springframework.extensions.webscripts.Cache;
import org.springframework.extensions.webscripts.DeclarativeWebScript;
import org.springframework.extensions.webscripts.Status;
import org.springframework.extensions.webscripts.WebScriptException;
import org.springframework.extensions.webscripts.WebScriptRequest;

public class Exportable extends DeclarativeWebScript
{
    private GoogleDocsService googledocsService;

    private final static String PARAM_MIMETYPE = "mimetype";
    private final static String MODEL_EXPORT_ACION = "export_action";

    private final static String ACTION_UPGRADE = "upgrade";
    private final static String ACTION_DOWNGRADE = "downgrade";
    private final static String ACTION_DEFAULT = "default";

    public void setGoogledocsService(GoogleDocsService googledocsService)
    {
        this.googledocsService = googledocsService;
    }

    @Override
    protected Map<String, Object> executeImpl(WebScriptRequest req, Status status, Cache cache)
    {
        Map<String, Object> model = new HashMap<String, Object>();

        try
        {
            if (googledocsService.isExportable(req.getParameter(PARAM_MIMETYPE)))
            {
                model.put(MODEL_EXPORT_ACION, ACTION_DEFAULT);
            }
            else
            {
                throw new WebScriptException(HttpStatus.SC_BAD_REQUEST,
                            "Content not exportable");
            }
        }
        catch (MustUpgradeFormatException mufe)
        {
            model.put(MODEL_EXPORT_ACION, ACTION_UPGRADE);
        }
        catch (MustDowngradeFormatException mdfe)
        {
            model.put(MODEL_EXPORT_ACION, ACTION_DOWNGRADE);
        }

        return model;
    }

}
