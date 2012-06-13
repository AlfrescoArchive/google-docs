<#assign el=args.htmlid?html>
<div id="${el}-body" class="gdtoolbar">
<div class="gd-container gdback"><button id="${el}-googledocs-back-button" class="gd-button gdback-button" name="back"><img src="${url.context}/res/modules/googledocs/images/bg_back.png" class="gb-back-image" align="absmiddle"/><img src="${url.context}/res/modules/googledocs/images/bg_alf.gif" class="gd-button-image" align="absmiddle"/>Alfresco</button></div>
<div class="gd-container gddiscard"><button id="${el}-googledocs-discard-button" class="gd-button gddiscard-button" name="discard">Discard Changes</button></div>
<div class="gd-container gdsave"><button id="${el}-googledocs-save-button" class="gd-button gdsave-button" name="save"><img src="${url.context}/res/modules/googledocs/images/bg_alf.gif" class="gd-button-image" align="absmiddle"/>Save to Alfresco</button></div>
</div>
<script type="text/javascript">//<![CDATA[
new Alfresco.GoogleDocs.Toolbar("${el}").setMessages(
   ${messages}
);
//]]></script>