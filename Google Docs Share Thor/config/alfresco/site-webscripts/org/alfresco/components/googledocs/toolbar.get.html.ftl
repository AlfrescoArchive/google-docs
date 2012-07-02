<#assign el=args.htmlid?html>
<script type="text/javascript">
function loggedIn()
{
   var gdocsWrapperDiv = document.getElementById("gdocs-wrapper");
   gdocsWrapperDiv.innerHTML = "<iframe class=\"gdocs-embed\" src=\"${editorURL!""}\"></iframe>";
}

function notLoggedIn()
{
   var gdocsWrapperDiv = document.getElementById("gdocs-wrapper");
   gdocsWrapperDiv.innerHTML = "<span style=\"color:red\">${msg("googledocs.auth.error.message")} <a id=\"${el}-googledocs-auth-link\">${msg("googledocs.auth.error.link")}</a></span>";
}

</script>
<div id="${el}-body" class="gdtoolbar">
   <div class="gdback"><button id="${el}-googledocs-back-button" class="gd-button gdback-button" name="back"><img src="${url.context}/res/modules/googledocs/images/back.png" /></button></div>
   <div class="gd-logo"></div>
   <div class="gdsave"><button id="${el}-googledocs-save-button" class="gd-button gd-button-default gdsave-button" name="save">${msg('googledocs.button.save')}</button></div>
   <div class="gddiscard"><button id="${el}-googledocs-discard-button" class="gd-button gddiscard-button" name="discard">${msg('googledocs.button.discard')}</button></div>
</div>
<div class="googledocs-Auth-Check">
    <img style="display:none;"
         onload="loggedIn()"
         onerror="notLoggedIn()"
         src="https://accounts.google.com/CheckCookie?continue=https%3A%2F%2Fwww.google.com%2Fintl%2Fen%2Fimages%2Flogos%2Faccounts_logo.png&followup=https%3A%2F%2Fwww.google.com%2Fintl%2Fen%2Fimages%2Flogos%2Faccounts_logo.png&chtml=LoginDoneHtml&checkedDomains=youtube&checkConnection=youtube%3A291%3A1" />
</div>
<script type="text/javascript">//<![CDATA[
new Alfresco.GoogleDocs.Toolbar("${el}").setOptions({
   nodeRef: "${page.url.args.nodeRef?js_string}",
   isVersioned: ${isVersioned?string}
}).setMessages(
   ${messages}
);
//]]></script>