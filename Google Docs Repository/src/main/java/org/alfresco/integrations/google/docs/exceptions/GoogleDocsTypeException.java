package org.alfresco.integrations.google.docs.exceptions;

public class GoogleDocsTypeException extends RuntimeException
{

    /**
     * 
     */
    private static final long serialVersionUID = 2875160232048415876L;

    public GoogleDocsTypeException(String message)
    {
        super(message);
    }

    public GoogleDocsTypeException(Throwable cause)
    {
        super(cause);
    }

    public GoogleDocsTypeException(String message, Throwable cause)
    {
        super(message, cause);
    }

}
