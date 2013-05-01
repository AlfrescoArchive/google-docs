/**
 * Copyright (C) 2005-2013 Alfresco Software Limited.
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

package org.alfresco.integrations.google.docs;


/**
 * @author Jared Ottley <jared.ottley@alfresco.com>
 */
public interface GoogleDocsConstants
{

    // OAuth2 Credential Store -- remotesystem name
    public static final String REMOTE_SYSTEM              = "googledocs";

    // Google OAuth2 redirect URI
    public static final String REDIRECT_URI               = "http://www.alfresco.com/google-auth-return.html";

    // Google OAuth2 Scopes
    public static final String SCOPE                      = "https://docs.google.com/feeds/ https://www.googleapis.com/auth/drive.file https://www.googleapis.com/auth/userinfo.profile https://www.googleapis.com/auth/userinfo.email";
    // Google docsService Client Name
    public static final String APPLICATION_NAME           = "Alfresco-GoogleDocs/2.1";
    
    // Google Document List API EndPoints
    public static final String BASE_URL                   = "https://docs.google.com/feeds";
    public static final String METADATA_URL               = BASE_URL + "/metadata/default";
    public static final String BASE_SPREADSHEET_URL       = "https://spreadsheets.google.com/feeds";
    public static final String URL_BASE_FEED              = BASE_URL + "/default/private/full";

    // Google contentTypes
    public static final String DOCUMENT_TYPE              = "document";
    public static final String PRESENTATION_TYPE          = "presentation";
    public static final String SPREADSHEET_TYPE           = "spreadsheet";

    // Google Docs Mimetypes
    public static final String DOCUMENT_MIMETYPE          = "application/vnd.google-apps.document";
    public static final String SPREADSHEET_MIMETYPE       = "application/vnd.google-apps.spreadsheet";
    public static final String PRESENTATION_MIMETYPE      = "application/vnd.google-apps.presentation";
    public static final String FOLDER_MIMETYPE            = "application/vnd.google-apps.folder";

    // Google New Document Names
    public static final String NEW_DOCUMENT_NAME          = "Untitled Document";
    public static final String NEW_PRESENTATION_NAME      = "Untitled Presentation";
    public static final String NEW_SPREADSHEET_NAME       = "Untitled Spreadsheet";

    // Google Drive Root Folder Id
    public static final String ROOT_FOLDER_ID             = "root";

    // Google Drive Alfresco Working Directory
    public static final String ALF_TEMP_FOLDER            = "Alfresco Temporary Files";
    public static final String ALF_TEMP_FOLDER_DESC       = "Alfresco Google Docs Temporary Work Files";

    /*
     * There is no standard 419. Some say not set (like Alfresco); Apache says WebDav INSUFFICIENT_SPACE_ON_RESOURCE.
     * 
     * Cut our loses and create our own.
     */
    public static final int    STATUS_INTEGIRTY_VIOLATION = 419;

}
