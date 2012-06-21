
package org.alfresco.integrations.google.docs.exceptions;


public class MustDowngradeFormatException
    extends RuntimeException
{

    /**
     * @author Jared Ottley <jared.ottley@alfresco.com>
     */
    private static final long serialVersionUID = 1L;


    public MustDowngradeFormatException()
    {
        super();
    }


    public MustDowngradeFormatException(String message, Throwable cause)
    {
        super(message, cause);
    }


    public MustDowngradeFormatException(String message)
    {
        super(message);
    }


    public MustDowngradeFormatException(Throwable cause)
    {
        super(cause);
    }

}
