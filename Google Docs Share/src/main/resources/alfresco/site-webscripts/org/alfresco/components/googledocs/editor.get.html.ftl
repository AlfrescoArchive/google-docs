<#assign el=args.htmlid?html>
<#if nodeRef?? && editorURL??>
<div id="${el}-gdocs-wrapper" class="gdocs-wrapper"></div>
<script type="text/javascript">//<![CDATA[
new Alfresco.GoogleDocs.Editor("${el}").setOptions({
   nodeRef: "${page.url.args.nodeRef?js_string}",
   editorURL: "${editorURL?js_string!''}"
}).setMessages(
   ${messages}
);
//]]></script>
</#if>
