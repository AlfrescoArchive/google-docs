<import resource="classpath:/alfresco/templates/org/alfresco/import/alfresco-util.js">

/*
 * Is the node versioned?
 */
function isVersioned()
{
   AlfrescoUtil.param('nodeRef');
   // TODO: Data webscript call to return whether the item is versioned or not
   //var aspectsResp = remote.call("slingshot/doclib/aspects/node/" + model.nodeRef.replace(":/", ""));
   var documentDetails = AlfrescoUtil.getNodeDetails(model.nodeRef, null);
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

function main()
{
   model.isVersioned = isVersioned();
}

main();