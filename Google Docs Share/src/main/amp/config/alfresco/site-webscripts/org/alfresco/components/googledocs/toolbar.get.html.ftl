<#assign el=args.htmlid?html>
<#if nodeRef??>
   <div id="${el}-messages" class="gdmessages"></div>
   <div id="${el}-body" class="gdtoolbar">
      <div class="gdback"><button id="${el}-googledocs-back-button" class="gd-button gdback-button" name="back"><img src="${url.context}/res/modules/googledocs/images/back.png" /></button></div>
      <div class="after-gdback"></div>
      <div class="gd-logo"></div>
      <div class="gdsaveTo"><button id="${el}-googledocs-saveTo-button" class="gd-button-mini gd-button-default gdsaveTo-button" name="saveTo">&#9663;</button></div>
      <div class="gdsave"><button id="${el}-googledocs-save-button" class="gd-button gd-button-default gdsave-button" name="save">${msg('googledocs.button.save')}</button></div>
      <div class="gdclose"><button id="${el}-googledocs-close-button" class="gd-button gdclose-button" name="close">${msg('googledocs.button.close')}</button></div>       
      <div class="before-gdsave"></div>
      <div id="${el}-new-version-dialog" class="gdversion-dialog"></div>
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
