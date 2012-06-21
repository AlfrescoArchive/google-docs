
package org.alfresco.integrations.google.docs;


/**
 * @author Jared Ottley <jared.ottley@alfresco.com>
 */
public interface GoogleDocsConstants
{

    // OAuth2 Credential Store -- remotesystem name
    public static final String REMOTE_SYSTEM             = "googledocs";

    // Google OAuth2 redirect URI
    public static final String REDIRECT_URI              = "http://www.ottleys.net/test.html";                                                                         // "http://www.alfresco.com";
    // Google OAuth2 Scopes
    public static final String SCOPE                     = "https://docs.google.com/feeds/ https://docs.googleusercontent.com/ https://spreadsheets.google.com/feeds/";
    // Google docsService Client Name
    public static final String APPLICATION_NAME          = "Alfresco-GoogleDocs/2.0";

    // Google Document List API EndPoints
    public static final String BASE_URL                  = "https://docs.google.com/feeds";
    public static final String METADATA_URL              = BASE_URL + "/metadata/default";
    public static final String BASE_SPREADSHEET_URL      = "https://spreadsheets.google.com/feeds";
    public static final String URL_CREATE_NEW_MEDIA      = BASE_URL + "/default/private/full";
    public static final String URL_CREATE_MEDIA          = BASE_URL + "/upload/create-session/default/private/full";
    public static final String URL_DOCUMENT_DOWNLOAD     = BASE_URL + "/download/documents/Export";
    public static final String URL_PRESENTATION_DOWNLOAD = BASE_URL + "/download/presentations/Export";
    public static final String URL_SPREADSHEET_DOWNLOAD  = BASE_SPREADSHEET_URL + "/download/spreadsheets/Export";

    // Google contentTypes
    public static final String DOCUMENT_TYPE             = "document";
    public static final String PRESENTATION_TYPE         = "presentation";
    public static final String SPREADSHEET_TYPE          = "spreadsheet";
    @Deprecated
    // Not yet Implemented
    public static final String FOLDER_TYPE               = "folder";

    // Google New Document Names
    public static final String NEW_DOCUMENT_NAME         = "Untitled Document";
    public static final String NEW_PRESENTATION_NAME     = "Untitled Presentation";
    public static final String NEW_SPREADSHEET_NAME      = "Untitled Spreadsheet";
    public static final String NEW_FOLDER_NAME           = "New Collection";

}
