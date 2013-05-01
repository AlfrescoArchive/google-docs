<#assign el=args.htmlid?html>
<#if nodeRef??>
<#if item??>
<#if editorURL??>
<div id="${el}-gdocs-wrapper" class="gdocs-wrapper"></div>
<script type="text/javascript">//<![CDATA[
new Alfresco.GoogleDocs.Editor("${el}").setOptions({
   nodeRef: "${page.url.args.nodeRef?js_string}",
   editorURL: "${editorURL?js_string!''}"
}).setMessages(
   ${messages}
);
//]]></script>
<#else>
<div class="status-banner theme-bg-color-2 theme-border-4">
${msg("banner.editor-url.not-found")}
</div>
</#if>
<#else>
<div class="status-banner theme-bg-color-2 theme-border-4">
${msg("banner.node-not-found")}
</div>
</#if>
</#if>
