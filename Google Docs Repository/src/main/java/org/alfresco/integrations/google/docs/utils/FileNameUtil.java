package org.alfresco.integrations.google.docs.utils;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.alfresco.integrations.google.docs.GoogleDocsConstants;

public class FileNameUtil
{
    private final static String FULL_PATTERN = "\\([0-9]*\\)";
    private final static String FIRST_DUP = " (1)";
    private final static String DOC_EXT = ".doc";
    private final static String DUP_NUMBER = "[0-9]*";
    
    
    //TODO this needs to be able to work.
    public static String IncrementFileName(String type, String filename, boolean usefileextension){
        String newname = null;
        if (type.equals(GoogleDocsConstants.DOCUMENT_TYPE)){
            if (filename.contains(FULL_PATTERN)){
                Pattern pattern = Pattern.compile(DUP_NUMBER);
                Matcher m = pattern.matcher(filename);

                newname = m.replaceFirst(String.valueOf(Integer.getInteger(m.group()).intValue() + 1)); 
                
            } else {
                String start = filename.substring(0, GoogleDocsConstants.NEW_DOCUMENT_NAME.length());
                newname = start.concat(FIRST_DUP);
                
                if (usefileextension){
                    newname = newname.concat(DOC_EXT);
                }
                
            }       
        }
        
        return newname;
    }
    
    
}
