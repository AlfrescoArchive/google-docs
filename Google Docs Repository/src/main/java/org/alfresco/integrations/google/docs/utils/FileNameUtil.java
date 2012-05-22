package org.alfresco.integrations.google.docs.utils;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.alfresco.integrations.google.docs.GoogleDocsConstants;
import org.alfresco.integrations.google.docs.exceptions.GoogleDocsServiceException;

public class FileNameUtil
{
    private final static String FULL_PATTERN = "\\(\\d++\\)";
    private final static String FIRST_DUP = " (1)";
    private final static String DOC_EXT = ".doc";
    private final static String XLS_EXT = ".xls";
    private final static String PPT_EXT = ".ppt";
    private final static String DUP_NUMBER = "\\d++";
    
    
    //TODO this needs to be able to work.
    public static String IncrementFileName(String type, String filename, boolean usefileextension){
        String newname = null;
        
        Pattern p = Pattern.compile(FULL_PATTERN);
        Matcher m = p.matcher(filename);
        
        if (type.equals(GoogleDocsConstants.DOCUMENT_TYPE)){
            if (m.find()){
                Pattern pattern = Pattern.compile(DUP_NUMBER);
                Matcher matcher = pattern.matcher(m.group());
                matcher.find();

                newname = m.replaceFirst("(" + (Integer.parseInt(matcher.group()) + 1) + ")"); 
                
            } else {
                String start = filename.substring(0, GoogleDocsConstants.NEW_DOCUMENT_NAME.length());
                newname = start.concat(FIRST_DUP);
                
                if (usefileextension){
                    newname = newname.concat(DOC_EXT);
                }
                
            }       
        } 
        else if(type.equals(GoogleDocsConstants.SPREADSHEET_TYPE))
        {
            if (m.find()){
                Pattern pattern = Pattern.compile(DUP_NUMBER);
                Matcher matcher = pattern.matcher(m.group());
                matcher.find();

                newname = m.replaceFirst("(" + (Integer.parseInt(matcher.group()) + 1) + ")"); 
                
            } else {
                String start = filename.substring(0, GoogleDocsConstants.NEW_SPREADSHEET_NAME.length());
                newname = start.concat(FIRST_DUP);
                
                if (usefileextension){
                    newname = newname.concat(XLS_EXT);
                }
                
            } 
        }
        else if(type.equals(GoogleDocsConstants.PRESENTATION_TYPE))
        {
            if (m.find()){
                Pattern pattern = Pattern.compile(DUP_NUMBER);
                Matcher matcher = pattern.matcher(m.group());
                matcher.find();

                newname = m.replaceFirst("(" + (Integer.parseInt(matcher.group()) + 1) + ")"); 
                
            } else {
                String start = filename.substring(0, GoogleDocsConstants.NEW_PRESENTATION_NAME.length());
                newname = start.concat(FIRST_DUP);
                
                if (usefileextension){
                    newname = newname.concat(PPT_EXT);
                }
                
            } 
        }
        else {
            throw new GoogleDocsServiceException("Content Type: " + type + " unknown.");
        }
        
        return newname;
    }
    
    public static String getNewFileName(String type)
    {
        String name = null;
        if (type.equals(GoogleDocsConstants.DOCUMENT_TYPE))
        {
            name = GoogleDocsConstants.NEW_DOCUMENT_NAME;
        } else if (type.equals(GoogleDocsConstants.SPREADSHEET_TYPE))
        {
            name = GoogleDocsConstants.NEW_SPREADSHEET_NAME;
        } else if (type.equals(GoogleDocsConstants.PRESENTATION_TYPE))
        {
            name = GoogleDocsConstants.NEW_PRESENTATION_NAME;
        } else {
            throw new GoogleDocsServiceException("Content type: " + type + " unknown");
        }
        
        return name;
    }
    
    
}
