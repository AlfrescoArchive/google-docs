
package org.alfresco.integrations.google.docs.utils;

import java.util.Comparator;

import com.google.gdata.data.docs.RevisionEntry;

public class RevisionEntryComparator implements Comparator<RevisionEntry>
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
