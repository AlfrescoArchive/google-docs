
package org.alfresco.integrations.google.docs.exceptions;


public class GoogleDocsAuthenticationException
    extends Exception
{

    /**
     * @author Jared Ottley <jared.ottley@alfresco.com>
     */
    private static final long serialVersionUID = 1L;


    public GoogleDocsAuthenticationException()
    {
        super();
    }


    public GoogleDocsAuthenticationException(final String message)
    {
        super(message);
    }


    public GoogleDocsAuthenticationException(final Throwable cause)
    {
        super(cause);
    }


    public GoogleDocsAuthenticationException(final String message, final Throwable cause)
    {
        super(message, cause);
    }

}
