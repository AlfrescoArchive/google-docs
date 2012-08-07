<#include "include/alfresco-template.ftl">
<@link rel="stylesheet" type="text/css" href="${url.context}/res/modules/googledocs/googledocs.css" />
<@templateHeader />

<@templateBody>
   <div id="alf-hd">
   <@region id="toolbar" scope="template"/>
   </div>
   <div id="bd">
      <@region id="editor" scope="template"/>
   </div>
</@> 


<@templateFooter>
 <!-- The iframe in the template above overlays the footer. Removed for now-->
</@>