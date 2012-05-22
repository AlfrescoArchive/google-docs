
package org.alfresco.integrations.google.docs.repo.jscript.app;


import java.io.Serializable;
import java.util.LinkedHashMap;
import java.util.Map;

import org.alfresco.integrations.google.docs.service.GoogleDocsService;
import org.alfresco.repo.jscript.app.CustomResponse;


public class GoogleDocsCustomResponse
    implements CustomResponse
{
    private GoogleDocsService googledocsService;


    public void setGoogledocsService(GoogleDocsService googledocsSerivce)
    {
        this.googledocsService = googledocsSerivce;
    }


    @Override
    public Serializable populate()
    {
        Map<String, Serializable> map = new LinkedHashMap<String, Serializable>(1);
        map.put("ImportFormats", googledocsService.getImportFormatsList());

        return (Serializable)map;
    }

}
