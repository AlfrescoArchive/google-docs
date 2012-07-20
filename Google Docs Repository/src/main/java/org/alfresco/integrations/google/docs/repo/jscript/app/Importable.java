/**
 * Copyright (C) 2005-2012 Alfresco Software Limited.
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
import org.alfresco.model.ContentModel;
import org.alfresco.repo.jscript.app.PropertyDecorator;
import org.alfresco.service.cmr.repository.ContentData;
import org.alfresco.service.cmr.repository.NodeRef;
import org.alfresco.service.cmr.repository.NodeService;

/**
 * 
 *
 * @author Jared Ottley <jared.ottley@alfresco.com>
 */
public class Importable
    implements PropertyDecorator
{
    private GoogleDocsService googledocsService;
    private NodeService       nodeService;


    public void setGoogledocsService(GoogleDocsService googledocsSerivce)
    {
        this.googledocsService = googledocsSerivce;
    }


    public void setNodeService(NodeService nodeService)
    {
        this.nodeService = nodeService;
    }


    @Override
    public Serializable decorate(NodeRef nodeRef, String propertyName, Serializable value)
    {
        Map<String, Serializable> map = new LinkedHashMap<String, Serializable>(1);

        ContentData contentData = (ContentData)nodeService.getProperty(nodeRef, ContentModel.PROP_CONTENT);
        boolean importable = googledocsService.isImportable(contentData.getMimetype());

        map.put("isImportable", importable);

        return (Serializable)map;
    }

}
