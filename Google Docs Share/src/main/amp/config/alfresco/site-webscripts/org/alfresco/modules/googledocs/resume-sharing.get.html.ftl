<#-- HTML template for the Resume Sharing Dialogue -->
<#assign el=args.htmlid?html>
<#if permissions?? && permissions?size gt 0>
<div id="${el}-dialog" class="resume-sharing">
   <div class="hd">
      <span id="${el}-header-span" class="resume-sharing-header">${msg("resumeSharing.dialogue.header")}</span>
   </div>
   <div class="bd">

      <form id="${el}-form">
         <input type="hidden" id="${el}-nodeRef-hidden" name="nodeRef" value=""/>

         <div class="bd">
            <#if showWarning>
            <div id="${el}-warning" class="warning-banner"><span>${msg("resumeSharing.warning.externalUsers")}</span></div>
            </#if>
            
            <#-- Role Column Header -->
            <div class="permissions-headings flat-button">
               <button type="button" id="${el}-select-button" value="${msg('label.select')}">${msg('label.select')}</button>
               <select id="${el}-select-menu">
                  <option value="all">${msg("label.selectAll")}</option>
                  <option value="none">${msg("label.selectNone")}</option>
               </select>
            </div>
            <div class="permissions-items">
            <#list permissions as permission>
            <div class="yui-gc permissions-list-item<#if permission.highlighted> external</#if>">
               <div class="yui-u first"><input id="${el}-checkbox-${permission_index}" type="checkbox" name="permissions" value="${permission.authorityType}|${permission.authorityId}" class="permission-checkbox"<#if permission.selected> checked="checked"</#if> /> <label for="${el}-checkbox-${permission_index}">${permission.authorityId}</label></div>
               <div class="yui-u"><span><button type="button" class="role-button" id="${el}-role-button-${permission_index}" name="${el}-role-button" value="${permission.roleName}">${msg("resumeSharing.role.${permission.roleName}")}</button>
               <select id="${el}-role-select-${permission_index}" name="${el}-role-select">
                  <option value="reader">${msg("resumeSharing.role.reader")}</option>
                  <option value="commenter">${msg("resumeSharing.role.commenter")}</option>
                  <option value="writer">${msg("resumeSharing.role.writer")}</option>
               </select></span></div>
            </div>
            </#list>
            </div>
            <#-- <div class="resume-options"><input id="${el}-checkbox-sendEmail" type="checkbox" name="sendEmail" value="1" checked="checked" /> <label for="${el}-checkbox-sendEmail">${msg("resumeSharing.sendEmail")}</label></div> -->
         </div>

         <div class="bdft">
            <input id="${el}-continue-button" type="button" value="${msg("resumeSharing.button.continue")}" />
            <input id="${el}-cancel-button" type="button" value="${msg("resumeSharing.button.cancel")}" />
         </div>

      </form>
   </div>
</div>
<script type="text/javascript">//<![CDATA[
Alfresco.util.addMessages(${messages}, "Alfresco.GoogleDocs.resumeSharing");
//]]></script>
</#if>
