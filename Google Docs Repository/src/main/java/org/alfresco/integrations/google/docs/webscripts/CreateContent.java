
package org.alfresco.integrations.google.docs.webscripts;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.alfresco.integrations.google.docs.GoogleDocsConstants;
import org.alfresco.integrations.google.docs.GoogleDocsModel;
import org.alfresco.integrations.google.docs.service.GoogleDocsService;
import org.alfresco.integrations.google.docs.utils.FileNameUtil;
import org.alfresco.model.ContentModel;
import org.alfresco.query.CannedQueryPageDetails;
import org.alfresco.query.PagingRequest;
import org.alfresco.query.PagingResults;
import org.alfresco.service.cmr.model.FileFolderService;
import org.alfresco.service.cmr.model.FileInfo;
import org.alfresco.service.cmr.repository.NodeRef;
import org.alfresco.service.cmr.repository.NodeService;
import org.alfresco.service.namespace.QName;
import org.alfresco.util.Pair;
import org.springframework.extensions.webscripts.Cache;
import org.springframework.extensions.webscripts.DeclarativeWebScript;
import org.springframework.extensions.webscripts.Status;
import org.springframework.extensions.webscripts.WebScriptException;
import org.springframework.extensions.webscripts.WebScriptRequest;

import com.google.gdata.data.docs.DocumentListEntry;

public class CreateContent extends DeclarativeWebScript
{
    private GoogleDocsService googledocsService;
    private NodeService nodeService;
    private FileFolderService fileFolderService;

    private final static String PARAM_TYPE = "contenttype";
    private final static String PARAM_PARENT = "parent";

    private final static String MODEL_NODEREF = "nodeRef";
    private final static String MODEL_EDITORURL = "editorURL";

    public void setGoogledocsService(GoogleDocsService googledocsService)
    {
        this.googledocsService = googledocsService;
    }

    public void setNodeService(NodeService nodeService)
    {
        this.nodeService = nodeService;
    }

    public void setFileFolderService(FileFolderService fileFolderService)
    {
        this.fileFolderService = fileFolderService;
    }

    @Override
    protected Map<String, Object> executeImpl(WebScriptRequest req, Status status, Cache cache)
    {
        Map<String, Object> model = new HashMap<String, Object>();

        DocumentListEntry documentEntry = null;

        String contentType = req.getParameter(PARAM_TYPE);
        NodeRef parentNodeRef = new NodeRef(req.getParameter(PARAM_PARENT));

        String nodeRef = null;
        String editorURL = null;

        if (contentType.equals(GoogleDocsConstants.DOCUMENT_TYPE))
        {
            String name = filenameHandler(contentType, parentNodeRef);

            documentEntry = googledocsService.createDocument(name);

            FileInfo newfile = fileFolderService.create(parentNodeRef, documentEntry.getTitle()
                        .getPlainText(), ContentModel.TYPE_CONTENT);

            googledocsService.getDocument(newfile.getNodeRef(), documentEntry.getResourceId());

            nodeDecorator(newfile.getNodeRef(), documentEntry);

            nodeRef = newfile.getNodeRef().toString();
            editorURL = documentEntry.getDocumentLink().getHref();

        }
        else if (contentType.equals(GoogleDocsConstants.SPREADSHEET_TYPE))
        {
            String name = filenameHandler(contentType, parentNodeRef);

            documentEntry = googledocsService.createSpreadSheet(name);

            FileInfo newfile = fileFolderService.create(parentNodeRef, documentEntry.getTitle()
                        .getPlainText(), ContentModel.TYPE_CONTENT);

            googledocsService.getSpreadSheet(newfile.getNodeRef(), documentEntry.getResourceId());

            nodeDecorator(newfile.getNodeRef(), documentEntry);

            nodeRef = newfile.getNodeRef().toString();
            editorURL = documentEntry.getDocumentLink().getHref();

        }
        else if (contentType.equals(GoogleDocsConstants.PRESENTATION_TYPE))
        {
            String name = filenameHandler(contentType, parentNodeRef);

            documentEntry = googledocsService.createPresentation(name);

            FileInfo newfile = fileFolderService.create(parentNodeRef, documentEntry.getTitle()
                        .getPlainText(), ContentModel.TYPE_CONTENT);

            googledocsService.getPresentation(newfile.getNodeRef(), documentEntry.getResourceId());

            nodeDecorator(newfile.getNodeRef(), documentEntry);

            nodeRef = newfile.getNodeRef().toString();
            editorURL = documentEntry.getDocumentLink().getHref();
        }
        else
        {
            throw new WebScriptException("Content Type Unknown.");
        }

        model.put(MODEL_NODEREF, nodeRef);
        model.put(MODEL_EDITORURL, editorURL);

        return model;
    }

    private String filenameHandler(String contentType, NodeRef parentNodeRef)
    {
        List<Pair<QName, Boolean>> sortProps = new ArrayList<Pair<QName, Boolean>>(1);
        sortProps.add(new Pair<QName, Boolean>(ContentModel.PROP_NAME, true));
        // TODO what kind of Patterns can we use?
        PagingResults<FileInfo> results = fileFolderService.list(parentNodeRef, true, false,
                    GoogleDocsConstants.NEW_DOCUMENT_NAME + "*", null, sortProps, new PagingRequest(
                                CannedQueryPageDetails.DEFAULT_PAGE_SIZE));

        List<FileInfo> page = results.getPage();
        FileInfo fileInfo = null;
        if (page.size() > 0)
        {
            fileInfo = page.get(0);
        }

        String name = null;
        if (fileInfo != null)
        {
            name = FileNameUtil.IncrementFileName(contentType, fileInfo.getName(), false);
        }

        return name;

    }

    private void nodeDecorator(NodeRef nodeRef, DocumentListEntry documentListEntry)
    {

        // Mark temporary until first save
        nodeService.addAspect(nodeRef, ContentModel.ASPECT_TEMPORARY, null);

        // Get the googleMetadata to reference the Node
        // TODO add eTag for discard/revision
        Map<QName, Serializable> aspectProperties = new HashMap<QName, Serializable>();
        aspectProperties.put(GoogleDocsModel.PROP_RESOURCE_ID, documentListEntry.getResourceId());
        nodeService.addAspect(nodeRef, GoogleDocsModel.ASPECT_GOOGLEDOCS, aspectProperties);

    }
}
