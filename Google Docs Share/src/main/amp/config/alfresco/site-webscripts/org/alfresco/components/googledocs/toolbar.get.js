<import resource="classpath:/alfresco/templates/org/alfresco/import/alfresco-util.js">

/**
 * Check to see if the node we are editing has the versionable aspect applied
 * 
 * @method isVersioned
 * @param documentDetails {object}  Document information as returned by AlfrescoUtil.getNodeDetails()
 * @returns {boolean} true if the document has the cm:versionable aspect applied to it, false otherwise
 */
function isVersioned(documentDetails)
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

/**
 * Main script entry point
 * @method main
 */
function main()
{
   AlfrescoUtil.param('nodeRef');
   AlfrescoUtil.param('site', null);
   var metadata = AlfrescoUtil.getNodeDetails(model.nodeRef, model.site);

   if (metadata)
   {
      model.item = metadata.item;
      model.editorURL = metadata.item.node.properties["gd2:editorURL"];
      model.version = metadata.item.version;
      model.isVersioned = isVersioned(metadata);
   }
   else
   {
       model.isVersioned = false;
   }
   
}

main();
