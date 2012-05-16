<#include "include/alfresco-template.ftl">
<@templateHeader>
   <@script type="text/javascript" src="${url.context}/res/modules/googledocs/googledocs-editor.js"></@script>
   <@link rel="stylesheet" type="text/css" href="${url.context}/res/modules/googledocs/googledocs-editor.css" />
</@>

<@templateBody>
   <div id="alf-hd">
   <@region id="toolbar" scope="template"/>
   </div>
   <div id="bd">
      <@region id="editor" scope="template"/>
   </div>
</@> 


<@templateFooter>
 <div id="alt-ft">
   <@region id="footer" scope="global"/>
 </div>
</@>