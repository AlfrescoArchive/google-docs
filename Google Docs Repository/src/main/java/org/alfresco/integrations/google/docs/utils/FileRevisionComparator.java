/**
 * Copyright (C) 2005-2015 Alfresco Software Limited.
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


import java.io.Serializable;
import java.util.Comparator;
import java.util.Date;

import com.google.api.client.util.DateTime;
import com.google.api.services.drive.model.Revision;

/**
 * @author Jared Ottley <jared.ottley@alfresco.com>
 */
public class FileRevisionComparator
    implements Comparator<Revision>, Serializable
{
    @Override
    public int compare(Revision entry1, Revision entry2)
    {
        DateTime entry1Revision = entry1.getModifiedDate();
        DateTime entry2Revision = entry2.getModifiedDate();

        //Google DateTime has no compare method. Convert to Java Date to compare.
        Date dt1 = new Date(entry1Revision.getValue());
        Date dt2 = new Date(entry2Revision.getValue());

        return dt1.compareTo(dt2);
    }

}
