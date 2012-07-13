<import resource="classpath:/alfresco/templates/org/alfresco/import/alfresco-util.js">

/*
 * Is the node versioned?
 */
function isVersioned()
{
   AlfrescoUtil.param('nodeRef');
   var documentDetails = AlfrescoUtil.getNodeDetails(model.nodeRef, null);
   // documentDetails may be null if user not logged in
   if (documentDetails)
   {
      var aspects = documentDetails.item.node.aspects;
      for (var i = 0; i < aspects.length; i++)
      {
         if (aspects[i] == "cm:versionable")
         {
            return true;
         }
      }
      return false;
   }
}

function main()
{
   model.isVersioned = isVersioned();
   
   AlfrescoUtil.param('nodeRef');
   AlfrescoUtil.param('site');
   var metadata = AlfrescoUtil.getNodeDetails(model.nodeRef, model.site);

   if (metadata)
   {
      model.editorURL = metadata.item.node.properties["g:editorURL"];
      model.version = metadata.item.version;
   }
   
}

main();