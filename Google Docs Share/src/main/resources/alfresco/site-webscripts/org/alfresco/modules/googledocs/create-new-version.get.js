<import resource="classpath:/alfresco/templates/org/alfresco/import/alfresco-util.js">

function main()
{
   AlfrescoUtil.param('version');
   
   var version = model.version,
      newMinor = "", newMajor = "";
   
   // Add version numbers to the version dialog as per GOOGLEDOCS-39
   if (version)
   {
      var majorMinor = version.split(".");
      if (majorMinor.length == 2)
      {
         // Set the version label in the dialog
         newMinor = majorMinor[0] + "." + (parseInt(majorMinor[1]) + 1),
            newMajor = "" + (parseInt(majorMinor[0]) + 1) + ".0";
         model.minorVersion = newMinor;
         model.majorVersion = newMajor;
      }
   }
   
}

main();