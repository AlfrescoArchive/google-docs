/**
 * Copyright (C) 2005-2012 Alfresco Software Limited.
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

package org.alfresco.integrations.google.docs.utils;


import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.alfresco.service.cmr.model.FileFolderService;
import org.alfresco.service.cmr.repository.MimetypeService;
import org.alfresco.service.cmr.repository.NodeRef;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;


/**
 * @author Jared Ottley <jared.ottley@alfresco.com>
 */
public class FileNameUtil
{
    private static final Log    log          = LogFactory.getLog(FileNameUtil.class);

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

        log.debug("Extension: " + extension);

        Pattern p = Pattern.compile(FULL_PATTERN + extension + "$");
        Matcher m = p.matcher(name);

        if (m.find())
        {
            log.debug("Matching filename found: " + name);
            Pattern pattern = Pattern.compile(DUP_NUMBER);
            Matcher matcher = pattern.matcher(m.group());
            matcher.find();

            newname = m.replaceFirst("-" + (Integer.parseInt(matcher.group()) + 1) + ".") + extension;
            log.debug("Increment filename from: " + name + " to: " + newname);
        }
        else
        {
            Pattern p_ = Pattern.compile(FULL_PATTERN.substring(0, 2) + "$");
            Matcher m_ = p_.matcher(name);

            if (m_.find())
            {
                log.debug("Matching filename found: " + name);
                Pattern pattern = Pattern.compile(DUP_NUMBER);
                Matcher matcher = pattern.matcher(m_.group());
                matcher.find();

                newname = m_.replaceFirst("-" + (Integer.parseInt(matcher.group()) + 1));
                log.debug("Increment filename from: " + name + " to: " + newname);
            }
            else
            {
                Pattern p_ext = Pattern.compile("\\." + extension + "$");
                Matcher m_ext = p_ext.matcher(name);

                if (m_ext.find())
                {
                    log.debug("Matching filename found: " + name);
                    String sansExtention = name.substring(0, name.length() - (extension.length() + 1));
                    newname = sansExtention.concat(FIRST_DUP).concat(extension);
                    log.debug("Increment filename from: " + name + " to: " + newname);
                }
                else
                {
                    log.debug("Matching filename not found: " + name);
                    newname = name.concat(FIRST_DUP.substring(0, 2));
                    log.debug("Increment filename from: " + name + " to: " + newname);
                }
            }
        }

        return newname;
    }

}
