
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
