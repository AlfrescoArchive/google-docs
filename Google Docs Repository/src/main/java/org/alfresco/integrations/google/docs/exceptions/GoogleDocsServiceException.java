package org.alfresco.integrations.google.docs.exceptions;

public class GoogleDocsServiceException extends RuntimeException
{

    /**
     * 
     */
    private static final long serialVersionUID = 1L;

    public GoogleDocsServiceException(String message)
    {
        super(message);
    }

    public GoogleDocsServiceException(Throwable cause)
    {
        super(cause);
    }

    public GoogleDocsServiceException(String message, Throwable cause)
    {
        super(message, cause);
    }

}
