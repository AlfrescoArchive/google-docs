
package org.alfresco.integrations.google.docs.service;


import java.io.File;
import java.io.IOException;
import java.io.Serializable;
import java.net.URL;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Collections;
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.alfresco.integrations.AbstractIntegration;
import org.alfresco.integrations.google.docs.GoogleDocsConstants;
import org.alfresco.integrations.google.docs.GoogleDocsModel;
import org.alfresco.integrations.google.docs.exceptions.GoogleDocsAuthenticationException;
import org.alfresco.integrations.google.docs.exceptions.GoogleDocsRefreshTokenException;
import org.alfresco.integrations.google.docs.exceptions.GoogleDocsServiceException;
import org.alfresco.integrations.google.docs.exceptions.GoogleDocsTypeException;
import org.alfresco.integrations.google.docs.exceptions.MustDowngradeFormatException;
import org.alfresco.integrations.google.docs.exceptions.MustUpgradeFormatException;
import org.alfresco.integrations.google.docs.utils.FileNameUtil;
import org.alfresco.integrations.google.docs.utils.RevisionEntryComparator;
import org.alfresco.model.ContentModel;
import org.alfresco.module.org_alfresco_module_cloud.analytics.Analytics;
import org.alfresco.query.CannedQueryPageDetails;
import org.alfresco.query.PagingRequest;
import org.alfresco.query.PagingResults;
import org.alfresco.repo.policy.BehaviourFilter;
import org.alfresco.service.cmr.lock.LockService;
import org.alfresco.service.cmr.lock.LockStatus;
import org.alfresco.service.cmr.lock.LockType;
import org.alfresco.service.cmr.model.FileFolderService;
import org.alfresco.service.cmr.model.FileInfo;
import org.alfresco.service.cmr.oauth2.OAuth2StoreService;
import org.alfresco.service.cmr.remotecredentials.OAuth2CredentialsInfo;
import org.alfresco.service.cmr.remoteticket.NoSuchSystemException;
import org.alfresco.service.cmr.repository.ContentReader;
import org.alfresco.service.cmr.repository.ContentWriter;
import org.alfresco.service.cmr.repository.MimetypeService;
import org.alfresco.service.cmr.repository.NodeRef;
import org.alfresco.service.cmr.repository.NodeService;
import org.alfresco.service.namespace.QName;
import org.alfresco.util.Pair;
import org.alfresco.util.TempFileProvider;
import org.apache.commons.httpclient.HttpStatus;
import org.springframework.core.io.Resource;
import org.springframework.social.ApiException;
import org.springframework.social.connect.Connection;
import org.springframework.social.google.docs.api.GoogleDocs;
import org.springframework.social.google.docs.connect.GoogleDocsConnectionFactory;
import org.springframework.social.oauth2.AccessGrant;
import org.springframework.social.oauth2.GrantType;
import org.springframework.social.oauth2.OAuth2Parameters;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;

import com.google.gdata.client.docs.DocsService;
import com.google.gdata.client.media.ResumableGDataFileUploader;
import com.google.gdata.data.MediaContent;
import com.google.gdata.data.PlainTextConstruct;
import com.google.gdata.data.docs.DocumentEntry;
import com.google.gdata.data.docs.DocumentListEntry;
import com.google.gdata.data.docs.MetadataEntry;
import com.google.gdata.data.docs.PresentationEntry;
import com.google.gdata.data.docs.RevisionEntry;
import com.google.gdata.data.docs.RevisionFeed;
import com.google.gdata.data.docs.SpreadsheetEntry;
import com.google.gdata.data.media.MediaFileSource;
import com.google.gdata.data.media.MediaSource;
import com.google.gdata.util.ServiceException;


/**
 * @author Jared Ottley <jared.ottley@alfresco.com>
 */
public class GoogleDocsServiceImpl
    extends AbstractIntegration
    implements GoogleDocsService
{
    // Services
    private OAuth2StoreService               oauth2StoreService;
    private GoogleDocsConnectionFactory      connectionFactory;
    private FileFolderService                fileFolderService;
    private NodeService                      nodeService;
    private LockService                      lockservice;
    private MimetypeService                  mimetypeService;
    private BehaviourFilter                  behaviourFilter;

    private FileNameUtil                     filenameUtil;

    // Property Mappings
    private Map<String, String>              importFormats     = new HashMap<String, String>();
    private Map<String, Map<String, String>> exportFormats     = new HashMap<String, Map<String, String>>();
    private Map<String, String>              upgradeMappings   = new HashMap<String, String>();
    private Map<String, String>              downgradeMappings = new HashMap<String, String>();

    // New Content
    private Resource                         newDocument;
    private Resource                         newSpreadsheet;
    private Resource                         newPresentation;

    // Time (in seconds) between last edit and now to consider edits as
    // concurrent
    private int                              idleThreshold     = 0;


    public void setImportFormats(Map<String, String> importFormats)
    {
        this.importFormats = importFormats;
    }


    public void setExportFormats(Map<String, Map<String, String>> exportFormats)
    {
        this.exportFormats = exportFormats;
    }


    public void setUpgradeMappings(Map<String, String> upgradeMappings)
    {
        this.upgradeMappings = upgradeMappings;
    }


    public void setDowngradeMappings(Map<String, String> downgradeMappings)
    {
        this.downgradeMappings = downgradeMappings;
    }


    public void setOauth2StoreService(OAuth2StoreService oauth2StoreService)
    {
        this.oauth2StoreService = oauth2StoreService;
    }


    public void setConnectionFactory(GoogleDocsConnectionFactory connectionFactory)
    {
        this.connectionFactory = connectionFactory;
    }


    public void setFileFolderService(FileFolderService fileFolderService)
    {
        this.fileFolderService = fileFolderService;
    }


    public void setNodeService(NodeService nodeService)
    {
        this.nodeService = nodeService;
    }


    public void setLockService(LockService lockService)
    {
        this.lockservice = lockService;
    }


    public void setMimetypeService(MimetypeService mimetypeService)
    {
        this.mimetypeService = mimetypeService;
    }


    public void setBehaviourFilter(BehaviourFilter behaviourFilter)
    {
        this.behaviourFilter = behaviourFilter;
    }


    public void setFileNameUtil(FileNameUtil fileNameUtil)
    {
        this.filenameUtil = fileNameUtil;
    }


    public ArrayList<String> getImportFormatsList()
    {
        return new ArrayList<String>(importFormats.keySet());
    }


    public void setNewDocument(Resource newDocument)
    {
        this.newDocument = newDocument;
    }


    public void setNewSpreadsheet(Resource newSpreadsheet)
    {
        this.newSpreadsheet = newSpreadsheet;
    }


    public void setNewPresentation(Resource newPresentation)
    {
        this.newPresentation = newPresentation;
    }


    public void setIdleThreshold(int idleThreshold)
    {
        this.idleThreshold = idleThreshold;
    }


    public boolean isImportable(String mimetype)
    {
        return importFormats.containsKey(mimetype);
    }


    private String getImportType(String mimetype)
    {
        return importFormats.get(mimetype);
    }


    public boolean isExportable(String mimetype)
        throws MustUpgradeFormatException,
            MustDowngradeFormatException
    {
        if (isUpgrade(mimetype))
        {
            throw new MustUpgradeFormatException();
        }
        else if (isDownGrade(mimetype))
        {
            throw new MustDowngradeFormatException();
        }

        String type = getImportType(mimetype);
        Set<String> exportMimetypes = getExportableMimeTypes(type);

        return exportMimetypes.contains(mimetype);
    }


    private Set<String> getExportableMimeTypes(String type)
    {
        Set<String> mimetypes = new HashSet<String>();

        if (exportFormats.containsKey(type))
        {
            mimetypes = exportFormats.get(type).keySet();
        }

        return mimetypes;
    }


    private boolean isUpgrade(String mimetype)
    {
        return upgradeMappings.containsKey(mimetype);
    }


    private boolean isDownGrade(String mimetype)
    {
        return downgradeMappings.containsKey(mimetype);
    }


    public String getContentType(NodeRef nodeRef)
    {
        String contentType = null;

        String mimetype = fileFolderService.getFileInfo(nodeRef).getContentData().getMimetype();

        contentType = importFormats.get(mimetype);

        return contentType;
    }


    private String getExportFormat(String contentType, String mimeType)
    {
        String exportFormat = null;

        mimeType = validateMimeType(mimeType);

        if (exportFormats.containsKey(contentType))
        {
            exportFormat = exportFormats.get(contentType).get(mimeType);
        }

        return exportFormat;
    }


    private String validateMimeType(String mimeType)
    {

        if (isDownGrade(mimeType))
        {
            mimeType = downgradeMappings.get(mimeType);
        }
        else if (isUpgrade(mimeType))
        {
            mimeType = upgradeMappings.get(mimeType);
        }

        return mimeType;
    }


    private Connection<GoogleDocs> getConnection()
        throws GoogleDocsAuthenticationException,
            GoogleDocsRefreshTokenException
    {
        Connection<GoogleDocs> connection = null;

        OAuth2CredentialsInfo credentialInfo = oauth2StoreService.getPersonalOAuth2Credentials(GoogleDocsConstants.REMOTE_SYSTEM);

        if (credentialInfo != null)
        {

            AccessGrant accessGrant = new AccessGrant(credentialInfo.getOAuthAccessToken());

            try
            {
                connection = connectionFactory.createConnection(accessGrant);
            }
            catch (ApiException apie)
            {
                if (apie.getCause() instanceof ServiceException)
                {
                    ServiceException se = (ServiceException)apie.getCause();
                    if (se.getHttpErrorCodeOverride() == HttpStatus.SC_UNAUTHORIZED)
                    {
                        try
                        {
                            accessGrant = refreshAccessToken();
                            connection = connectionFactory.createConnection(accessGrant);
                        }
                        catch (GoogleDocsRefreshTokenException gdrte)
                        {
                            throw gdrte;
                        }
                    }
                }
            }
        }

        return connection;
    }


    public boolean isAuthenticated()
    {
        boolean authenticated = false;

        OAuth2CredentialsInfo credentialInfo = oauth2StoreService.getPersonalOAuth2Credentials(GoogleDocsConstants.REMOTE_SYSTEM);

        if (credentialInfo != null)
        {
            authenticated = true;
        }

        return authenticated;
    }


    public String getAuthenticateUrl(String state)
    {
        String authenticateUrl = null;

        if (state != null)
        {

            /*
             * When we change to spring social 1.0.2 OAuth2Parameters will need
             * to be updated OAuth2Parameters parameters = new
             * OAuth2Parameters(); parameters.setRedirectUri(REDIRECT_URI);
             * parameters.setScope(SCOPE); parameters.setState(state);
             */

            // TODO Add offline

            MultiValueMap<String, String> additionalParameters = new LinkedMultiValueMap<String, String>(1);
            additionalParameters.add("access_type", "offline");

            OAuth2Parameters parameters = new OAuth2Parameters(GoogleDocsConstants.REDIRECT_URI, GoogleDocsConstants.SCOPE, state, additionalParameters);
            parameters.getAdditionalParameters();
            authenticateUrl = connectionFactory.getOAuthOperations().buildAuthenticateUrl(GrantType.AUTHORIZATION_CODE, parameters);

        }

        return authenticateUrl;
    }


    public boolean completeAuthentication(String access_token)
        throws GoogleDocsServiceException
    {
        boolean authenticationComplete = false;

        AccessGrant accessGrant = connectionFactory.getOAuthOperations().exchangeForAccess(access_token, GoogleDocsConstants.REDIRECT_URI, null);

        Date expiresIn = null;

        if (accessGrant.getExpireTime() != null)
        {
            if (accessGrant.getExpireTime() > 0L)
            {
                expiresIn = new Date(new Date().getTime() + accessGrant.getExpireTime());
            }
        }

        try
        {
            oauth2StoreService.storePersonalOAuth2Credentials(GoogleDocsConstants.REMOTE_SYSTEM, accessGrant.getAccessToken(), accessGrant.getRefreshToken(), expiresIn, new Date());

            authenticationComplete = true;
        }
        catch (NoSuchSystemException nsse)
        {
            throw new GoogleDocsServiceException(nsse.getMessage());
        }

        return authenticationComplete;
    }


    private AccessGrant refreshAccessToken()
        throws GoogleDocsAuthenticationException,
            GoogleDocsRefreshTokenException
    {
        OAuth2CredentialsInfo credentialInfo = oauth2StoreService.getPersonalOAuth2Credentials(GoogleDocsConstants.REMOTE_SYSTEM);

        if (credentialInfo.getOAuthRefreshToken() != null)
        {

            AccessGrant accessGrant = null;
            try
            {

                accessGrant = connectionFactory.getOAuthOperations().refreshAccess(credentialInfo.getOAuthRefreshToken(), null, null);
            }
            catch (ApiException apie)
            {
                if (apie.getCause() instanceof ServiceException)
                {
                    ServiceException se = (ServiceException)apie.getCause();
                    if (se.getHttpErrorCodeOverride() == HttpStatus.SC_UNAUTHORIZED)
                    {
                        throw new GoogleDocsAuthenticationException("Token Refresh Failed.");
                    }
                }
            }

            if (accessGrant != null)
            {
                Date expiresIn = null;

                if (accessGrant.getExpireTime() != null)
                {
                    if (accessGrant.getExpireTime() > 0L)
                    {
                        expiresIn = new Date(new Date().getTime() + accessGrant.getExpireTime());
                    }
                }

                try
                {
                    oauth2StoreService.storePersonalOAuth2Credentials(GoogleDocsConstants.REMOTE_SYSTEM, accessGrant.getAccessToken(), credentialInfo.getOAuthRefreshToken(), expiresIn, new Date());
                }
                catch (NoSuchSystemException nsse)
                {
                    throw nsse;
                }
            }
            else
            {
                throw new GoogleDocsAuthenticationException("No Access Grant Returned.");
            }

            return accessGrant;

        }
        else
        {
            throw new GoogleDocsRefreshTokenException("No Refresh Token Provided");
        }
    }


    private DocsService getDocsService(Connection<GoogleDocs> connection)
    {
        DocsService docsService = null;

        try
        {
            docsService = connection.getApi().setAuthentication(new DocsService(GoogleDocsConstants.APPLICATION_NAME));
        }
        catch (ApiException apie)
        {
            throw apie;
        }

        return docsService;

    }


    private DocumentListEntry createContent(String type, String name)
        throws GoogleDocsServiceException,
            GoogleDocsTypeException,
            GoogleDocsAuthenticationException,
            GoogleDocsRefreshTokenException,
            IOException
    {
        DocsService docsService = getDocsService(getConnection());

        DocumentListEntry entry = null;
        if (type.equals(GoogleDocsConstants.DOCUMENT_TYPE))
        {
            if (name == null)
            {
                name = GoogleDocsConstants.NEW_DOCUMENT_NAME;
            }
            entry = new DocumentEntry();
        }
        else if (type.equals(GoogleDocsConstants.SPREADSHEET_TYPE))
        {
            if (name == null)
            {
                name = GoogleDocsConstants.NEW_SPREADSHEET_NAME;
            }
            entry = new SpreadsheetEntry();
        }
        else if (type.equals(GoogleDocsConstants.PRESENTATION_TYPE))
        {
            if (name == null)
            {
                name = GoogleDocsConstants.NEW_PRESENTATION_NAME;
            }
            entry = new PresentationEntry();
        }

        if (entry != null)
        {
            entry.setTitle(new PlainTextConstruct(name));
            // TODO In production this should be set to true;
            entry.setHidden(false);

            try
            {
                return docsService.insert(new URL(GoogleDocsConstants.URL_CREATE_NEW_MEDIA), entry);
            }
            catch (IOException ioe)
            {
                throw ioe;
            }
            catch (ServiceException se)
            {
                throw new GoogleDocsServiceException(se.getMessage(), se.getHttpErrorCodeOverride());
            }
        }
        else
        {
            throw new GoogleDocsTypeException("Type Unknown: " + type + ". Must be document, spreadsheet, presentation or folder.");
        }
    }


    public DocumentListEntry createDocument(NodeRef nodeRef)
        throws GoogleDocsServiceException,
            GoogleDocsTypeException,
            GoogleDocsAuthenticationException,
            GoogleDocsRefreshTokenException,
            IOException
    {
        DocumentListEntry documentListEntry = createContent(GoogleDocsConstants.DOCUMENT_TYPE, fileFolderService.getFileInfo(nodeRef).getName());

        try
        {
            ContentWriter writer = fileFolderService.getWriter(nodeRef);
            writer.setMimetype("application/msword");
            writer.putContent(newDocument.getInputStream());

            // Cloud Analytics Service
            Analytics.record_UploadDocument("application/msword", newDocument.contentLength(), false);
        }
        catch (IOException ioe)
        {
            throw ioe;
        }

        return documentListEntry;
    }


    public DocumentListEntry createSpreadSheet(NodeRef nodeRef)
        throws GoogleDocsServiceException,
            GoogleDocsTypeException,
            GoogleDocsAuthenticationException,
            GoogleDocsRefreshTokenException,
            IOException
    {
        DocumentListEntry documentListEntry = createContent(GoogleDocsConstants.SPREADSHEET_TYPE, fileFolderService.getFileInfo(nodeRef).getName());

        try
        {
            ContentWriter writer = fileFolderService.getWriter(nodeRef);
            writer.setMimetype("application/vnd.ms-excel");
            writer.putContent(newSpreadsheet.getInputStream());

            // Cloud Analtics Service
            Analytics.record_UploadDocument("application/vnd.ms-excel", newSpreadsheet.contentLength(), false);
        }
        catch (IOException ioe)
        {
            throw ioe;
        }

        return documentListEntry;
    }


    public DocumentListEntry createPresentation(NodeRef nodeRef)
        throws GoogleDocsServiceException,
            GoogleDocsTypeException,
            GoogleDocsAuthenticationException,
            GoogleDocsRefreshTokenException,
            IOException
    {
        DocumentListEntry documentListEntry = createContent(GoogleDocsConstants.PRESENTATION_TYPE, fileFolderService.getFileInfo(nodeRef).getName());

        try
        {
            ContentWriter writer = fileFolderService.getWriter(nodeRef);
            writer.setMimetype("application/vnd.ms-powerpoint");
            writer.putContent(newPresentation.getInputStream());

            // Cloud Analytics Service
            Analytics.record_UploadDocument("application/vnd.ms-powerpoint", newPresentation.contentLength(), false);
        }
        catch (IOException ioe)
        {
            throw ioe;
        }

        return documentListEntry;
    }


    private void getDocument(NodeRef nodeRef, String resourceID)
        throws GoogleDocsAuthenticationException,
            GoogleDocsServiceException,
            GoogleDocsRefreshTokenException,
            IOException
    {

        DocsService docsService = getDocsService(getConnection());

        try
        {
            MediaContent mc = new MediaContent();

            String mimeType = null;
            String exportFormat = null;

            mimeType = validateMimeType(fileFolderService.getFileInfo(nodeRef).getContentData().getMimetype());
            exportFormat = getExportFormat(getContentType(nodeRef), mimeType);

            mc.setUri(GoogleDocsConstants.URL_DOCUMENT_DOWNLOAD + "?docID=" + resourceID.substring(resourceID.lastIndexOf(':') + 1)
                      + "&exportFormat=" + exportFormat);

            MediaSource ms = docsService.getMedia(mc);

            ContentWriter writer = fileFolderService.getWriter(nodeRef);
            writer.setMimetype(mimeType);
            writer.putContent(ms.getInputStream());

            DocumentListEntry documentListEntry = getDocumentListEntry(resourceID);

            renameNode(nodeRef, documentListEntry.getTitle().getPlainText());

            deleteContent(nodeRef, documentListEntry);

            if (nodeService.hasAspect(nodeRef, ContentModel.ASPECT_TEMPORARY))
            {
                nodeService.removeAspect(nodeRef, ContentModel.ASPECT_TEMPORARY);
            }

        }
        catch (IOException ioe)
        {
            throw ioe;
        }
        catch (ServiceException se)
        {
            throw new GoogleDocsServiceException(se.getMessage(), se.getHttpErrorCodeOverride());
        }
    }


    public void getDocument(NodeRef nodeRef)
        throws GoogleDocsAuthenticationException,
            GoogleDocsServiceException,
            GoogleDocsRefreshTokenException,
            IOException
    {
        // TODO Wrap with try for null
        String resourceID = nodeService.getProperty(nodeRef, GoogleDocsModel.PROP_RESOURCE_ID).toString();

        getDocument(nodeRef, resourceID);
    }


    private void getSpreadSheet(NodeRef nodeRef, String resourceID)
        throws GoogleDocsAuthenticationException,
            GoogleDocsServiceException,
            GoogleDocsRefreshTokenException,
            IOException
    {

        DocsService docsService = getDocsService(getConnection());

        try
        {
            MediaContent mc = new MediaContent();

            String mimeType = null;
            String exportFormat = null;

            mimeType = validateMimeType(fileFolderService.getFileInfo(nodeRef).getContentData().getMimetype());
            exportFormat = getExportFormat(getContentType(nodeRef), mimeType);

            mc.setUri(GoogleDocsConstants.URL_SPREADSHEET_DOWNLOAD + "?key="
                      + resourceID.substring(resourceID.lastIndexOf(':') + 1) + "&exportFormat=" + exportFormat);

            MediaSource ms = docsService.getMedia(mc);

            ContentWriter writer = fileFolderService.getWriter(nodeRef);
            writer.setMimetype(mimeType);
            writer.putContent(ms.getInputStream());

            if (nodeService.hasAspect(nodeRef, ContentModel.ASPECT_TEMPORARY))
            {
                nodeService.removeAspect(nodeRef, ContentModel.ASPECT_TEMPORARY);
            }

            DocumentListEntry documentListEntry = getDocumentListEntry(resourceID);

            renameNode(nodeRef, documentListEntry.getTitle().getPlainText());

            deleteContent(nodeRef, documentListEntry);

        }
        catch (IOException ioe)
        {
            throw ioe;
        }
        catch (ServiceException se)
        {
            throw new GoogleDocsServiceException(se.getMessage(), se.getHttpErrorCodeOverride());
        }
    }


    public void getSpreadSheet(NodeRef nodeRef)
        throws GoogleDocsAuthenticationException,
            GoogleDocsServiceException,
            GoogleDocsRefreshTokenException,
            IOException
    {
        // TODO Wrap with try for null
        String resourceID = nodeService.getProperty(nodeRef, GoogleDocsModel.PROP_RESOURCE_ID).toString();

        getSpreadSheet(nodeRef, resourceID);
    }


    private void getPresentation(NodeRef nodeRef, String resourceID)
        throws GoogleDocsAuthenticationException,
            GoogleDocsServiceException,
            GoogleDocsRefreshTokenException,
            IOException
    {

        DocsService docsService = getDocsService(getConnection());

        try
        {
            MediaContent mc = new MediaContent();

            String mimeType = null;
            String exportFormat = null;

            mimeType = validateMimeType(fileFolderService.getFileInfo(nodeRef).getContentData().getMimetype());
            exportFormat = getExportFormat(getContentType(nodeRef), mimeType);

            mc.setUri(GoogleDocsConstants.URL_PRESENTATION_DOWNLOAD + "?docID="
                      + resourceID.substring(resourceID.lastIndexOf(':') + 1) + "&exportFormat=" + exportFormat);

            MediaSource ms = docsService.getMedia(mc);

            ContentWriter writer = fileFolderService.getWriter(nodeRef);
            writer.setMimetype(mimeType);
            writer.putContent(ms.getInputStream());

            DocumentListEntry documentListEntry = getDocumentListEntry(resourceID);

            renameNode(nodeRef, documentListEntry.getTitle().getPlainText());

            deleteContent(nodeRef, documentListEntry);

            if (nodeService.hasAspect(nodeRef, ContentModel.ASPECT_TEMPORARY))
            {
                nodeService.removeAspect(nodeRef, ContentModel.ASPECT_TEMPORARY);
            }

        }
        catch (IOException ioe)
        {
            throw ioe;
        }
        catch (ServiceException se)
        {
            throw new GoogleDocsServiceException(se.getMessage(), se.getHttpErrorCodeOverride());
        }
    }


    public void getPresentation(NodeRef nodeRef)
        throws GoogleDocsAuthenticationException,
            GoogleDocsServiceException,
            GoogleDocsRefreshTokenException,
            IOException
    {
        // TODO Wrap with try for null
        String resourceID = nodeService.getProperty(nodeRef, GoogleDocsModel.PROP_RESOURCE_ID).toString();

        getPresentation(nodeRef, resourceID);
    }


    public DocumentListEntry uploadFile(NodeRef nodeRef)
        throws GoogleDocsAuthenticationException,
            GoogleDocsServiceException,
            GoogleDocsRefreshTokenException,
            IOException
    {
        DocsService docsService = getDocsService(getConnection());

        DocumentListEntry uploaded = null;

        // It makes me want to cry that the don't support inputStreams.
        File file = null;

        try
        {
            // Get the read
            ContentReader reader = fileFolderService.getReader(nodeRef);
            file = File.createTempFile(nodeRef.getId(), ".tmp", TempFileProvider.getTempDir());
            reader.getContent(file);

            // Get the mimetype
            FileInfo fileInfo = fileFolderService.getFileInfo(nodeRef);
            String mimetype = fileInfo.getContentData().getMimetype();

            // Create MediFileSource
            MediaFileSource mediaFile = new MediaFileSource(file, mimetype);

            DocumentListEntry entry = new DocumentListEntry();
            entry.setTitle(new PlainTextConstruct(fileInfo.getName()));
            // In prodcution this should always be true
            entry.setHidden(false);

            // Will this be Sync?
            ResumableGDataFileUploader uploader = new ResumableGDataFileUploader.Builder(docsService, new URL(GoogleDocsConstants.URL_CREATE_MEDIA), mediaFile, entry).chunkSize(10485760L).build();
            uploader.start();

            while (!uploader.isDone())
            {
                try
                {
                    Thread.sleep(100);
                }
                catch (InterruptedException ie)
                {
                    throw new GoogleDocsServiceException(ie.getMessage());
                }
            }
            uploaded = uploader.getResponse(DocumentListEntry.class);

            // Cloud Analytics Service
            Analytics.record_UploadDocument(fileInfo.getContentData().getMimetype(), fileInfo.getContentData().getSize(), true);

        }
        catch (IOException ioe)
        {
            throw ioe;
        }
        catch (ServiceException se)
        {
            throw new GoogleDocsServiceException(se.getMessage(), se.getHttpErrorCodeOverride());
        }
        finally
        {
            if (file != null)
            {
                file.delete();
            }
        }

        return uploaded;
    }


    public boolean deleteContent(NodeRef nodeRef, DocumentListEntry documentListEntry)
        throws GoogleDocsAuthenticationException,
            GoogleDocsServiceException,
            GoogleDocsRefreshTokenException,
            IOException
    {
        boolean deleted = false;

        DocsService docsService = getDocsService(getConnection());

        try
        {
            docsService.delete(new URL(GoogleDocsConstants.URL_CREATE_NEW_MEDIA
                                       + "/"
                                       + documentListEntry.getResourceId().substring(documentListEntry.getResourceId().lastIndexOf(':') + 1)
                                       + "?delete=true"), documentListEntry.getEtag());

            unDecorateNode(nodeRef);
            deleted = true;
        }
        catch (IOException ioe)
        {
            throw ioe;
        }
        catch (ServiceException se)
        {
            throw new GoogleDocsServiceException(se.getMessage(), se.getHttpErrorCodeOverride());
        }

        return deleted;
    }


    public void decorateNode(NodeRef nodeRef, DocumentListEntry documentListEntry, boolean newcontent)
    {
        behaviourFilter.disableBehaviour(nodeRef, ContentModel.ASPECT_VERSIONABLE);
        try
        {
            if (newcontent)
            {
                // Mark temporary until first save
                nodeService.addAspect(nodeRef, ContentModel.ASPECT_TEMPORARY, null);
            }

            // Get the googleMetadata to reference the Node
            // TODO Do we need to add eTag for discard/revision
            Map<QName, Serializable> aspectProperties = new HashMap<QName, Serializable>();
            aspectProperties.put(GoogleDocsModel.PROP_RESOURCE_ID, documentListEntry.getResourceId());
            aspectProperties.put(GoogleDocsModel.PROP_EDITORURL, documentListEntry.getDocumentLink().getHref());
            nodeService.addAspect(nodeRef, GoogleDocsModel.ASPECT_GOOGLEDOCS, aspectProperties);
        }
        finally
        {
            behaviourFilter.enableBehaviour(nodeRef, ContentModel.ASPECT_VERSIONABLE);
        }
    }


    private void unDecorateNode(NodeRef nodeRef)
    {
        if (nodeService.hasAspect(nodeRef, GoogleDocsModel.ASPECT_GOOGLEDOCS))
        {
            nodeService.removeAspect(nodeRef, GoogleDocsModel.ASPECT_GOOGLEDOCS);
        }
    }


    public void lockNode(NodeRef nodeRef)
    {
        nodeService.setProperty(nodeRef, GoogleDocsModel.PROP_LOCKED, true);
        lockservice.lock(nodeRef, LockType.READ_ONLY_LOCK);
    }


    public void unlockNode(NodeRef nodeRef)
    {
        lockservice.unlock(nodeRef);
        nodeService.setProperty(nodeRef, GoogleDocsModel.PROP_LOCKED, false);
    }


    /**
     * Is the node locked by Googledocs? If the document is marked locked in the
     * model, but not locked in the repository, the locked property is set to
     * false
     * 
     * @param nodeRef
     * @return
     */
    public boolean isLockedByGoogleDocs(NodeRef nodeRef)
    {

        boolean locked = false;

        if ((Boolean)nodeService.getProperty(nodeRef, GoogleDocsModel.PROP_LOCKED))
        {
            LockStatus lockStatus = lockservice.getLockStatus(nodeRef);
            if (lockStatus.equals(LockStatus.NO_LOCK))
            {
                // fix broken lock
                nodeService.setProperty(nodeRef, GoogleDocsModel.PROP_LOCKED, false);
            }
            else
            {
                locked = true;
            }
        }

        return locked;
    }


    /**
     * @param nodeRef
     * @return Will return false is the document is not locked
     */
    public boolean isGoogleDocsLockOwner(NodeRef nodeRef)
    {
        boolean isOwner = false;

        if (isLockedByGoogleDocs(nodeRef))
        {
            LockStatus lockStatus = lockservice.getLockStatus(nodeRef);
            if (lockStatus.equals(LockStatus.LOCK_OWNER))
            {
                isOwner = true;
            }
        }

        return isOwner;
    }


    /**
     * Find nodes using duplicate name in same context (folder/space).
     * 
     * @param nodeRef
     * @param name if null, name will be pulled from nodeRef
     * @return
     */
    private NodeRef findLastDuplicate(NodeRef nodeRef, String name)
    {
        NodeRef lastDup = null;

        List<Pair<QName, Boolean>> sortProps = new ArrayList<Pair<QName, Boolean>>(1);
        sortProps.add(new Pair<QName, Boolean>(ContentModel.PROP_NAME, false));

        if (name == null)
        {
            name = fileFolderService.getFileInfo(nodeRef).getName();
        }

        PagingResults<FileInfo> results = fileFolderService.list(nodeService.getPrimaryParent(nodeRef).getParentRef(), true, false, addWildCardInName(name, fileFolderService.getFileInfo(nodeRef).getContentData().getMimetype()), null, sortProps, new PagingRequest(CannedQueryPageDetails.DEFAULT_PAGE_SIZE));

        List<FileInfo> page = results.getPage();
        FileInfo fileInfo = null;
        if (page.size() > 0)
        {
            fileInfo = page.get(0);
            lastDup = fileInfo.getNodeRef();

        }

        return lastDup;
    }


    /**
     * Insert wildcard '*' into filename between name and extension
     * 
     * @param name
     * @param mimetype
     * @return
     */
    private String addWildCardInName(String name, String mimetype)
    {
        String extension = mimetypeService.getExtension(mimetype);
        return name.substring(0, name.length() - (extension.length() + 1)).concat("*." + extension);
    }


    /**
     * When the file format has changed or a new document is created we need to
     * either change to extension or add an extesion
     * 
     * @param name
     * @param office2007Pattern
     * @param office1997Pattern
     * @param office1997extension
     * @return
     */
    private String MSofficeExtensionHandler(String name, String office2007Pattern, String office1997Pattern,
            String office1997extension)
    {
        Pattern pattern = Pattern.compile(office2007Pattern);
        Matcher matcher = pattern.matcher(name);

        if (matcher.find())
        {
            name = name.substring(0, name.length() - 1);
        }
        else
        {
            Pattern _pattern = Pattern.compile(office1997Pattern);
            Matcher _matcher = _pattern.matcher(name);

            if (!_matcher.find())
            {
                name = name.concat(office1997extension);
            }
        }

        return name;
    }


    /**
     * Modify the file extension is the file mimetype has changed. If the name
     * was changed while being edited in google docs updated the name in
     * Alfresco. If the name is already in use in the current folder, append
     * -{number} to the name or if it already has a -{number} increment the
     * number for the new file
     * 
     * @param nodeRef
     * @param name New name
     */
    private void renameNode(NodeRef nodeRef, String name)
    {
        // not all file types can be round tripped. This should Correct
        // extensions on files where the format is modifed or Add an extension
        // to file types where there is no extension
        FileInfo fileInfo = fileFolderService.getFileInfo(nodeRef);
        String mimetype = fileInfo.getContentData().getMimetype();

        if (mimetype.equals("application/msword"))
        {
            name = MSofficeExtensionHandler(name, "\\.docx$", "\\.doc$", ".doc");
        }
        else if (mimetype.equals("application/vnd.ms-excel"))
        {
            name = MSofficeExtensionHandler(name, "\\.xlsx$", "\\.xls$", ".xls");
        }
        else if (mimetype.equals("application/vnd.ms-powerpoint"))
        {
            name = MSofficeExtensionHandler(name, "\\.pptx$", "\\.ppt$", ".ppt");
        }
        else if (mimetype.equals("application/vnd.oasis.opendocument.text"))
        {
            Pattern odt_pattern = Pattern.compile("\\.odt$");
            Matcher odt_matcher = odt_pattern.matcher(name);

            if (!odt_matcher.find())
            {
                Pattern sxw_pattern = Pattern.compile("\\.sxw$");
                Matcher sxw_matcher = sxw_pattern.matcher(name);

                if (sxw_matcher.find())
                {
                    name = name.substring(0, name.length() - 4);
                    name = name.concat(".odt");
                }
            }
        }


        NodeRef lastDup = findLastDuplicate(nodeRef, name);

        if (lastDup != null)
        {
            if (!lastDup.equals(fileInfo.getNodeRef()))
            {
                name = filenameUtil.incrementFileName(fileFolderService.getFileInfo(lastDup).getName(), fileInfo.getContentData().getMimetype());
            }
        }

        // if there is no change in the name we don't want to make a change in
        // the repo
        if (!fileInfo.getName().equals(name))
        {
            nodeService.setProperty(nodeRef, ContentModel.PROP_NAME, name);
        }
    }


    public boolean hasConcurrentEditors(NodeRef nodeRef)
        throws GoogleDocsAuthenticationException,
            GoogleDocsRefreshTokenException,
            IOException
    {
        DocsService docsService = getDocsService(getConnection());

        String resourceID = nodeService.getProperty(nodeRef, GoogleDocsModel.PROP_RESOURCE_ID).toString();

        boolean concurrentChange = false;

        try
        {
            RevisionFeed revisionFeed = docsService.getFeed(new URL(GoogleDocsConstants.URL_CREATE_NEW_MEDIA + "/"
                                                                    + resourceID.substring(resourceID.lastIndexOf(':') + 1)
                                                                    + "/revisions"), RevisionFeed.class);

            List<RevisionEntry> revisionList = revisionFeed.getEntries();

            if (revisionList.size() > 1)
            {
                Collections.sort(revisionList, new RevisionEntryComparator());

                // Find any revisions occuring within the last 'idleThreshold'
                // seconds
                List<RevisionEntry> workingList = new ArrayList<RevisionEntry>();

                Calendar bufferTime = Calendar.getInstance();
                bufferTime.add(Calendar.SECOND, -idleThreshold);

                for (RevisionEntry entry : revisionList)
                {
                    if (new Date(entry.getUpdated().getValue()).after(new Date(bufferTime.getTimeInMillis())))
                    {
                        workingList.add(entry);
                    }
                    else
                    {
                        // once we past 'idleThreshold' seconds get out of here
                        break;
                    }
                }

                // If there any revisions that occured within the last
                // 'idleThreshold' seconds of time....
                if (workingList.size() > 0)
                {

                    // Filter the current user from the list
                    for (int i = workingList.size() - 1; i >= 0; i--)
                    {
                        RevisionEntry revisionEntry = workingList.get(i);
                        String username = getUserMetadata().getAuthors().get(0).getName();

                        // if there is no author -- the entry is the initial
                        // creation
                        if (revisionEntry.getAuthors().size() > 0)
                        {
                            if (revisionEntry.getAuthors().get(0).getName().equals(username))
                            {
                                workingList.remove(i);
                            }
                        }
                        else
                        {
                            workingList.remove(i);
                        }
                    }
                }

                // Are there are changes by other users within the last
                // 'idleThreshold' seconds
                if (workingList.size() > 0)
                {
                    concurrentChange = true;
                }

            }
            else
            {
                String username = getUserMetadata().getAuthors().get(0).getName();

                // if the authors list is empty -- the author was the original
                // creator and it is the initial copy
                if (!revisionList.get(0).getAuthors().isEmpty())
                {

                    if (!revisionList.get(0).getAuthors().get(0).getName().equals(username))
                    {
                        Calendar bufferTime = Calendar.getInstance();
                        bufferTime.add(Calendar.SECOND, -idleThreshold);

                        if (new Date(revisionList.get(0).getUpdated().getValue()).before(new Date(bufferTime.getTimeInMillis())))
                        {
                            concurrentChange = true;
                        }
                    }
                }
            }

        }
        catch (IOException ioe)
        {
            throw ioe;
        }
        catch (ServiceException se)
        {
            new GoogleDocsServiceException(se.getMessage(), se.getHttpErrorCodeOverride());
        }

        return concurrentChange;
    }


    public DocumentListEntry getDocumentListEntry(String resourceID)
        throws IOException,
            GoogleDocsServiceException,
            GoogleDocsAuthenticationException,
            GoogleDocsRefreshTokenException
    {
        DocsService docsService = getDocsService(getConnection());

        DocumentListEntry documentListEntry = null;

        try
        {
            documentListEntry = docsService.getEntry(new URL(GoogleDocsConstants.URL_CREATE_NEW_MEDIA + "/"
                                                             + resourceID.substring(resourceID.lastIndexOf(':') + 1)), DocumentListEntry.class);
        }
        catch (IOException ioe)
        {
            throw ioe;
        }
        catch (ServiceException se)
        {
            throw new GoogleDocsServiceException(se.getMessage(), se.getHttpErrorCodeOverride());
        }
        return documentListEntry;
    }


    public MetadataEntry getUserMetadata()
        throws GoogleDocsAuthenticationException,
            GoogleDocsRefreshTokenException,
            IOException
    {
        DocsService docsService = getDocsService(getConnection());

        MetadataEntry metadataEntry = null;

        try
        {
            metadataEntry = docsService.getEntry(new URL(GoogleDocsConstants.METADATA_URL), MetadataEntry.class);
        }
        catch (IOException ioe)
        {
            throw ioe;
        }
        catch (ServiceException se)
        {
            new GoogleDocsServiceException(se.getMessage(), se.getHttpErrorCodeOverride());
        }

        return metadataEntry;
    }


    @Override
    public void removeApp(boolean removeContent)
    {
        // TODO Auto-generated method stub

    }
}
