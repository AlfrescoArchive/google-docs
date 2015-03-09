<import resource="classpath:alfresco/enterprise/webscripts/org/alfresco/enterprise/repository/admin/admin-common.lib.js">

/**
 * Repository Admin Console
 *
 * GoogleDocs GET method
 */
Admin.initModel(
    "Alfresco:Type=Configuration,Category=googledocs,id1=default",
    ["googledocs.enabled", "googledocs.version", "integration.googleDocs.idleThresholdSeconds"],
    "admin-googledocs"
);
