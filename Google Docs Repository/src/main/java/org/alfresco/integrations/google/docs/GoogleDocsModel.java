
package org.alfresco.integrations.google.docs;

import org.alfresco.service.namespace.QName;

public interface GoogleDocsModel
{
    public static final String ORG_GOOGLEDOCS_MODEL_1_0_URI = "http://www.alfresco.org/model/googledocs/1.0";

    public static final QName ASPECT_GOOGLEDOCS = QName.createQName(ORG_GOOGLEDOCS_MODEL_1_0_URI,
                "googledocs");

    public static final QName PROP_RESOURCE_ID = QName.createQName(ORG_GOOGLEDOCS_MODEL_1_0_URI, "resourceID");
    public static final QName PROP_LOCKED = QName.createQName(ORG_GOOGLEDOCS_MODEL_1_0_URI, "locked");
    public static final QName PROP_EDITORURL = QName.createQName(ORG_GOOGLEDOCS_MODEL_1_0_URI, "editorURL");
}
