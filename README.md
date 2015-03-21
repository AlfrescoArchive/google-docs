Alfresco Google Docs Module
===========================

Description
-----------

This extension adds the ability to edit supported content items in Google Docs&trade; to the Alfresco repository and Share interface.

When building from source you must include your own google OAuth client secret json file. Instructions for creating it can be found at [https://developers.google.com/drive/web/auth/web-server#create_a_client_id_and_client_secret]()

The content of the generated file should be added to the appropriate `client_secret-{community,enterprise}.json` file found in `Google Docs Repository/src/main/oauth-client-resources/`.

Building
-----------

When building the amps (`mvn clean package`) you should also include a combination of the following profiles (`-P`). If not include, the defaults are applied.

**Platform**

`community` Build amps for Alfresco Community (default)

`enterprise` Build amps for Alfresco Enterprise

**Version**

`4` Build amps for version 4.2.x of Alfresco

`5` Build amps for version 5.x of Alfresco (default)

Example: `mvn clean package -Penterprise,5`
	
***Note:** Amps built from this source are not supported by Alfresco. They are built and used at your own risk. Supported releases of the enterprise amps can be found at the Alfresco Customer Portal.*

