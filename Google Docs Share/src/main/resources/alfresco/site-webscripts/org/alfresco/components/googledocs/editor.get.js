<import resource="classpath:/alfresco/templates/org/alfresco/import/alfresco-util.js">

function main()
{
        AlfrescoUtil.param('nodeRef');
        AlfrescoUtil.param('site', null);
        var metadata = AlfrescoUtil.getNodeDetails(model.nodeRef, model.site);

        if (metadata)
        {
                model.item = metadata.item;
                if (metadata.item.node.properties["gd2:editorURL"])
                {
                    model.editorURL = metadata.item.node.properties["gd2:editorURL"];
                }
        }
}

main();
