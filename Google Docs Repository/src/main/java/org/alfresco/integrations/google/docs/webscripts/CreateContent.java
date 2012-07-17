
package org.alfresco.integrations.google.docs.webscripts;


import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.alfresco.integrations.google.docs.GoogleDocsConstants;
import org.alfresco.integrations.google.docs.exceptions.GoogleDocsAuthenticationException;
import org.alfresco.integrations.google.docs.exceptions.GoogleDocsRefreshTokenException;
import org.alfresco.integrations.google.docs.exceptions.GoogleDocsServiceException;
import org.alfresco.integrations.google.docs.exceptions.GoogleDocsTypeException;
import org.alfresco.integrations.google.docs.service.GoogleDocsService;
import org.alfresco.integrations.google.docs.utils.FileNameUtil;
import org.alfresco.model.ContentModel;
import org.alfresco.query.CannedQueryPageDetails;
import org.alfresco.query.PagingRequest;
import org.alfresco.query.PagingResults;
import org.alfresco.service.cmr.model.FileFolderService;
import org.alfresco.service.cmr.model.FileInfo;
import org.alfresco.service.cmr.repository.NodeRef;
import org.alfresco.service.namespace.QName;
import org.alfresco.util.Pair;
import org.apache.commons.httpclient.HttpStatus;
import org.springframework.extensions.webscripts.Cache;
import org.springframework.extensions.webscripts.DeclarativeWebScript;
import org.springframework.extensions.webscripts.Status;
import org.springframework.extensions.webscripts.WebScriptException;
import org.springframework.extensions.webscripts.WebScriptRequest;

import com.google.gdata.data.docs.DocumentListEntry;


/**
 * @author Jared Ottley <jared.ottley@alfresco.com>
 */
public class CreateContent
    extends DeclarativeWebScript
{
    private GoogleDocsService   googledocsService;
    private FileFolderService   fileFolderService;

    private FileNameUtil        fileNameUtil;

    private final static String PARAM_TYPE    = "contenttype";
    private final static String PARAM_PARENT  = "parent";

    private final static String MODEL_NODEREF = "nodeRef";


    public void setGoogledocsService(GoogleDocsService googledocsService)
    {
        this.googledocsService = googledocsService;
    }


    public void setFileFolderService(FileFolderService fileFolderService)
    {
        this.fileFolderService = fileFolderService;
    }


    public void setFileNameUtil(FileNameUtil fileNameUtil)
    {
        this.fileNameUtil = fileNameUtil;
    }


    @Override
    protected Map<String, Object> executeImpl(WebScriptRequest req, Status status, Cache cache)
    {
        Map<String, Object> model = new HashMap<String, Object>();

        String contentType = req.getParameter(PARAM_TYPE);
        NodeRef parentNodeRef = new NodeRef(req.getParameter(PARAM_PARENT));

        FileInfo fileInfo = null;
        DocumentListEntry documentEntry = null;
        try
        {
            if (contentType.equals(GoogleDocsConstants.DOCUMENT_TYPE))
            {
                String name = filenameHandler(contentType, parentNodeRef);
                fileInfo = fileFolderService.create(parentNodeRef, name, ContentModel.TYPE_CONTENT);

                documentEntry = googledocsService.createDocument(fileInfo.getNodeRef());

                googledocsService.decorateNode(fileInfo.getNodeRef(), documentEntry, true);
            }
            else if (contentType.equals(GoogleDocsConstants.SPREADSHEET_TYPE))
            {
                String name = filenameHandler(contentType, parentNodeRef);
                fileInfo = fileFolderService.create(parentNodeRef, name, ContentModel.TYPE_CONTENT);

                documentEntry = googledocsService.createSpreadSheet(fileInfo.getNodeRef());

                googledocsService.decorateNode(fileInfo.getNodeRef(), documentEntry, true);
            }
            else if (contentType.equals(GoogleDocsConstants.PRESENTATION_TYPE))
            {
                String name = filenameHandler(contentType, parentNodeRef);
                fileInfo = fileFolderService.create(parentNodeRef, name, ContentModel.TYPE_CONTENT);

                documentEntry = googledocsService.createPresentation(fileInfo.getNodeRef());

                googledocsService.decorateNode(fileInfo.getNodeRef(), documentEntry, true);
            }
            else
            {
                throw new WebScriptException(HttpStatus.SC_UNSUPPORTED_MEDIA_TYPE, "Content Type Not Found.");
            }

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
        catch (GoogleDocsTypeException gdte)
        {
            throw new WebScriptException(HttpStatus.SC_UNSUPPORTED_MEDIA_TYPE, gdte.getMessage());
        }
        catch (GoogleDocsAuthenticationException gdae)
        {
            throw new WebScriptException(HttpStatus.SC_BAD_GATEWAY, gdae.getMessage());
        }
        catch (GoogleDocsRefreshTokenException gdrte)
        {
            throw new WebScriptException(HttpStatus.SC_BAD_GATEWAY, gdrte.getMessage());
        }
        catch (IOException ioe)
        {
            throw new WebScriptException(HttpStatus.SC_INTERNAL_SERVER_ERROR, ioe.getMessage(), ioe);
        }

        googledocsService.lockNode(fileInfo.getNodeRef());

        model.put(MODEL_NODEREF, fileInfo.getNodeRef().toString());

        return model;
    }


    /**
     * Look for duplicate file names for the type in the folder/space
     * 
     * @param contentType
     * @param parentNodeRef
     * @return
     */
    private String filenameHandler(String contentType, NodeRef parentNodeRef)
    {
        List<Pair<QName, Boolean>> sortProps = new ArrayList<Pair<QName, Boolean>>(1);
        sortProps.add(new Pair<QName, Boolean>(ContentModel.PROP_NAME, false));

        PagingResults<FileInfo> results = null;
        if (contentType.equals(GoogleDocsConstants.DOCUMENT_TYPE))
        {
            results = fileFolderService.list(parentNodeRef, true, false, GoogleDocsConstants.NEW_DOCUMENT_NAME + "*", null, sortProps, new PagingRequest(CannedQueryPageDetails.DEFAULT_PAGE_SIZE));
        }
        else if (contentType.equals(GoogleDocsConstants.SPREADSHEET_TYPE))
        {
            results = fileFolderService.list(parentNodeRef, true, false, GoogleDocsConstants.NEW_SPREADSHEET_NAME + "*", null, sortProps, new PagingRequest(CannedQueryPageDetails.DEFAULT_PAGE_SIZE));
        }
        else if (contentType.equals(GoogleDocsConstants.PRESENTATION_TYPE))
        {
            results = fileFolderService.list(parentNodeRef, true, false, GoogleDocsConstants.NEW_PRESENTATION_NAME + "*", null, sortProps, new PagingRequest(CannedQueryPageDetails.DEFAULT_PAGE_SIZE));
        }
        else
        {
            throw new WebScriptException(HttpStatus.SC_UNSUPPORTED_MEDIA_TYPE, "Content type: " + contentType + " unknown.");
        }

        List<FileInfo> page = results.getPage();
        FileInfo fileInfo = null;
        if (page.size() > 0)
        {
            fileInfo = page.get(0);
        }

        String name = null;
        if (fileInfo != null)
        {
            name = fileNameUtil.incrementFileName(fileInfo.getNodeRef());
        }
        else
        {
            name = getNewFileName(contentType);
        }

        return name;

    }


    /**
     * Get the default new content name
     * 
     * @param type
     * @return
     */
    private static String getNewFileName(String type)
    {
        String name = null;
        if (type.equals(GoogleDocsConstants.DOCUMENT_TYPE))
        {
            name = GoogleDocsConstants.NEW_DOCUMENT_NAME;
        }
        else if (type.equals(GoogleDocsConstants.SPREADSHEET_TYPE))
        {
            name = GoogleDocsConstants.NEW_SPREADSHEET_NAME;
        }
        else if (type.equals(GoogleDocsConstants.PRESENTATION_TYPE))
        {
            name = GoogleDocsConstants.NEW_PRESENTATION_NAME;
        }

        return name;
    }
}
