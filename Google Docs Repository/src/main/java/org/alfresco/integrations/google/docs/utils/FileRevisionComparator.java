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

package org.alfresco.integrations.google.docs.utils;


import java.util.Comparator;
import java.util.Date;

import org.springframework.social.google.api.drive.FileRevision;

/**
 * @author Jared Ottley <jared.ottley@alfresco.com>
 */
public class FileRevisionComparator
    implements Comparator<FileRevision>
{
    @Override
    public int compare(FileRevision entry1, FileRevision entry2)
    {
        Date entry1Revision = entry1.getModifiedDate();
        Date entry2Revision = entry2.getModifiedDate();

        return entry1Revision.compareTo(entry2Revision);
    }

}
