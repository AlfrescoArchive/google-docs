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

import com.google.gdata.data.docs.RevisionEntry;


/**
 * @author Jared Ottley <jared.ottley@alfresco.com>
 */
public class RevisionEntryComparator
    implements Comparator<RevisionEntry>
{
    private static final String PREFIX_TITLE = "Revision ";


    @Override
    public int compare(RevisionEntry entry1, RevisionEntry entry2)
    {
        String entry1Revision = entry1.getTitle().getPlainText().substring(PREFIX_TITLE.length());
        String entry2Revision = entry2.getTitle().getPlainText().substring(PREFIX_TITLE.length());

        int results = Long.valueOf(entry1Revision).compareTo(Long.valueOf(entry2Revision));

        if (results > 0)
        {
            return -1;
        }
        else if (results < 0)
        {
            return 1;
        }
        else
        {
            return 0;
        }
    }

}
