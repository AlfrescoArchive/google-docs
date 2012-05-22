<import resource="classpath:/alfresco/templates/org/alfresco/import/alfresco-util.js">

function main()
{
        AlfrescoUtil.param('nodeRef');
        AlfrescoUtil.param('site');
        var metadata = AlfrescoUtil.getNodeDetails(model.nodeRef, model.site);

        if (metadata)
        {
                model.editorURL = metadata.item.node.properties["g:editorURL"];
        }
}

main();