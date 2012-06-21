
package org.alfresco.integrations.google.docs.utils;


import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.alfresco.service.cmr.model.FileFolderService;
import org.alfresco.service.cmr.repository.MimetypeService;
import org.alfresco.service.cmr.repository.NodeRef;


/**
 * @author Jared Ottley <jared.ottley@alfresco.com>
 */
public class FileNameUtil
{
    private final static String FULL_PATTERN = "\\-\\d++\\.";
    private final static String FIRST_DUP    = "-1.";
    private final static String DUP_NUMBER   = "\\d++";

    private MimetypeService     mimetypeService;
    private FileFolderService   filefolderService;


    public void setMimetypeService(MimetypeService mimetypeService)
    {
        this.mimetypeService = mimetypeService;
    }


    public void setFileFolderService(FileFolderService filefolderService)
    {
        this.filefolderService = filefolderService;
    }


    public String incrementFileName(NodeRef nodeRef)
    {
        String name = filefolderService.getFileInfo(nodeRef).getName();
        String mimetype = filefolderService.getFileInfo(nodeRef).getContentData().getMimetype();

        return incrementFileName(name, mimetype);
    }


    public String incrementFileName(String name, String mimetype)
    {
        String newname = null;
        String extension = mimetypeService.getExtension(mimetype);

        Pattern p = Pattern.compile(FULL_PATTERN + extension + "$");
        Matcher m = p.matcher(name);

        if (m.find())
        {
            Pattern pattern = Pattern.compile(DUP_NUMBER);
            Matcher matcher = pattern.matcher(m.group());
            matcher.find();

            newname = m.replaceFirst("-" + (Integer.parseInt(matcher.group()) + 1) + ".") + extension;
        }
        else
        {
            Pattern p_ = Pattern.compile(FULL_PATTERN.substring(0, 2) + "$");
            Matcher m_ = p_.matcher(name);

            if (m_.find())
            {
                Pattern pattern = Pattern.compile(DUP_NUMBER);
                Matcher matcher = pattern.matcher(m_.group());
                matcher.find();

                newname = m_.replaceFirst("-" + (Integer.parseInt(matcher.group()) + 1));
            }
            else
            {
                Pattern p_ext = Pattern.compile("\\." + extension + "$");
                Matcher m_ext = p_ext.matcher(name);

                if (m_ext.find())
                {
                    String sansExtention = name.substring(0, name.length() - (extension.length() + 1));
                    newname = sansExtention.concat(FIRST_DUP).concat(extension);
                }
                else
                {
                    newname = name.concat(FIRST_DUP.substring(0, 2));
                }
            }
        }

        return newname;
    }

}
