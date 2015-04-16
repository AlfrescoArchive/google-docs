/**
 * Copyright (C) 2005-2015 Alfresco Software Limited.
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


import org.alfresco.service.namespace.QName;


/**
 * @author Jared Ottley <jared.ottley@alfresco.com>
 */
public interface GoogleDocsModel
{
    public static final String ORG_GOOGLEDOCS_MODEL_2_0_URI = "http://www.alfresco.org/model/googledocs/2.0";

    public static final QName  ASPECT_EDITING_IN_GOOGLE     = QName.createQName(ORG_GOOGLEDOCS_MODEL_2_0_URI, "editingInGoogle");

    public static final QName  ASPECT_SHARED_IN_GOOGLE      = QName.createQName(ORG_GOOGLEDOCS_MODEL_2_0_URI, "sharedInGoogle");

    public static final QName  PROP_RESOURCE_ID             = QName.createQName(ORG_GOOGLEDOCS_MODEL_2_0_URI, "resourceID");
    public static final QName  PROP_LOCKED                  = QName.createQName(ORG_GOOGLEDOCS_MODEL_2_0_URI, "locked");
    public static final QName  PROP_EDITORURL               = QName.createQName(ORG_GOOGLEDOCS_MODEL_2_0_URI, "editorURL");
    public static final QName  PROP_DRIVE_WORKING_FOLDER    = QName.createQName(ORG_GOOGLEDOCS_MODEL_2_0_URI, "driveWorkingDir");
    public static final QName  PROP_REVISION_ID             = QName.createQName(ORG_GOOGLEDOCS_MODEL_2_0_URI, "revisionID");

    public static final QName  PROP_PERMISSIONS             = QName.createQName(ORG_GOOGLEDOCS_MODEL_2_0_URI, "permissions");
    public static final QName  PROP_CURRENT_PERMISSIONS     = QName.createQName(ORG_GOOGLEDOCS_MODEL_2_0_URI, "currentPermissions");
}
