
package org.alfresco.integrations.google.docs.exceptions;


public class GoogleDocsServiceException
    extends Exception
{

    /**
     * @author Jared Ottley <jared.ottley@alfresco.com>
     */
    private static final long serialVersionUID = 1L;

    private int               passedStatusCode = -1;


    /**
     * Returns the status code passed to the exception. Return -1 if no status
     * code passed.
     * 
     * @return
     */
    public int getPassedStatusCode()
    {
        return passedStatusCode;
    }


    public GoogleDocsServiceException(String message)
    {
        super(message);
    }


    public GoogleDocsServiceException(String message, int passedStatusCode)
    {
        super(message);
        this.passedStatusCode = passedStatusCode;
    }


    public GoogleDocsServiceException(Throwable cause)
    {
        super(cause);
    }


    public GoogleDocsServiceException(Throwable cause, int passedStatusCode)
    {
        super(cause);
        this.passedStatusCode = passedStatusCode;
    }


    public GoogleDocsServiceException(String message, Throwable cause)
    {
        super(message, cause);
    }


    public GoogleDocsServiceException(String message, Throwable cause, int passedStatusCode)
    {
        super(message, cause);
        this.passedStatusCode = passedStatusCode;
    }

}
