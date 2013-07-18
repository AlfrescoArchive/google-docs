<#assign el=args.htmlid?html>
<div id="${el}-dialog" class="google-docs-version">
   <div class="hd">${msg("label.header")}</div>
   <div class="bd">
      <form id="${args.htmlid}-form" action="" method="POST">
         <div id="${el}-versionSection-div"> 
            <div class="yui-gd">
               <div class="yui-u first">
                  <span>${msg("label.version")}</span>
               </div>
               <div class="yui-u">
                  <input id="${el}-minorVersion-radioButton" type="radio" name="majorVersion" value="false" checked="checked" tabindex="0"/>
                  <label for="${el}-minorVersion-radioButton" id="${el}-minorVersion">${msg("label.minorVersion", minorVersion)}</label>
               </div>
            </div>
            <div class="yui-gd">
               <div class="yui-u first">&nbsp;
               </div>
               <div class="yui-u">
                  <input id="${el}-majorVersion-radioButton" type="radio" name="majorVersion" value="true" tabindex="0"/>
                  <label for="${el}-majorVersion-radioButton" id="${el}-majorVersion">${msg("label.majorVersion", majorVersion)}</label>
               </div>
            </div>
            <div class="yui-gd">
               <div class="yui-u first">
                  <label for="${el}-description-textarea">${msg("label.comments")}</label>
               </div>
               <div class="yui-u">
                  <textarea id="${el}-description-textarea" name="description" cols="80" rows="4" tabindex="0"></textarea>
               </div>
            </div>
         </div>
         <div class="bdft">
            <input id="${el}-nodeRef" type="hidden" name="nodeRef" value="" />
            <input id="${el}-override" type="hidden" name="override" value="false" />
            <input id="${el}-ok" type="submit" value="${msg("button.ok")}" tabindex="0"/>
            <input id="${el}-cancel" type="button" value="${msg("button.cancel")}" tabindex="0"/>
         </div>
      </form>
   </div>
</div>
