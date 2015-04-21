/**
 * Copyright (C) 2005-2015 Alfresco Software Limited.
 * 
 * This file is part of Alfresco
 * 
 * Alfresco is free software: you can redistribute it and/or modify it under the
 * terms of the GNU Lesser General Public License as published by the Free
 * Software Foundation, either version 3 of the License, or (at your option) any
 * later version.
 * 
 * Alfresco is distributed in the hope that it will be useful, but WITHOUT ANY
 * WARRANTY; without even the implied warranty of MERCHANTABILITY or FITNESS FOR
 * A PARTICULAR PURPOSE. See the GNU Lesser General Public License for more
 * details.
 * 
 * You should have received a copy of the GNU Lesser General Public License
 * along with Alfresco. If not, see <http://www.gnu.org/licenses/>.
 */

package org.alfresco.integrations.google.docs.repo.jscript.app;


import java.io.Serializable;
import java.util.LinkedHashMap;
import java.util.Map;

import org.alfresco.integrations.google.docs.service.GoogleDocsService;
import org.alfresco.repo.jscript.app.CustomResponse;

/**
 * @author Jared Ottley <jared.ottley@alfresco.com>
 */
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
        Map<String, Serializable> map = new LinkedHashMap<String, Serializable>(2);
        map.put("enabled", (Serializable)googledocsService.isEnabled());
        map.put("importFormats", (Serializable)googledocsService.getImportFormats());

        return (Serializable)map;
    }

}
