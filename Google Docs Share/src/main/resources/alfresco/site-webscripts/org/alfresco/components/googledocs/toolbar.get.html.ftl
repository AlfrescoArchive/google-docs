<#assign el=args.htmlid?html>
<#if nodeRef??>
   <div id="${el}-body" class="gdtoolbar">
      <div class="gdback"><button id="${el}-googledocs-back-button" class="gd-button gdback-button" name="back"><img src="${url.context}/res/modules/googledocs/images/back.png" /></button></div>
      <div class="after-gdback"></div>
      <div class="gd-logo"></div>
      <div class="gdsave"><button id="${el}-googledocs-save-button" class="gd-button gd-button-default gdsave-button" name="save">${msg('googledocs.button.save')}</button></div>
      <div class="gddiscard"><button id="${el}-googledocs-discard-button" class="gd-button gddiscard-button" name="discard">${msg('googledocs.button.discard')}</button></div>
      <div class="before-gdsave"></div>
   </div>
   <script type="text/javascript">//<![CDATA[
   new Alfresco.GoogleDocs.Toolbar("${el}").setOptions({
      nodeRef: <#if item??>"${nodeRef?js_string}"<#else>null</#if>,
      site: "${site!""?js_string}",
      isVersioned: ${isVersioned?string},
      version: "${version!""?js_string}"
   }).setMessages(
      ${messages}
   );
   //]]></script>
</#if>
