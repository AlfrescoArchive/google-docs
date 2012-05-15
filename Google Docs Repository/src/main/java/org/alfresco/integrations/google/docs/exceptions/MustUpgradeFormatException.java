
package org.alfresco.integrations.google.docs.exceptions;

public class MustUpgradeFormatException extends RuntimeException
{

    /**
     * 
     */
    private static final long serialVersionUID = 1L;

    public MustUpgradeFormatException()
    {
        super();
    }

    public MustUpgradeFormatException(String message, Throwable cause)
    {
        super(message, cause);
    }

    public MustUpgradeFormatException(String message)
    {
        super(message);
    }

    public MustUpgradeFormatException(Throwable cause)
    {
        super(cause);
    }

}
