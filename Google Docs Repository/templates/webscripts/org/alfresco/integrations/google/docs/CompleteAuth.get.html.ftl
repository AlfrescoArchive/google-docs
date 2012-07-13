<!DOCTYPE html PUBLIC "-//W3C//DTD XHTML 1.0 Strict//EN" "http://www.w3.org/TR/xhtml1/DTD/xhtml1-strict.dtd">
<html xmlns="http://www.w3.org/1999/xhtml">
<head>
   <meta http-equiv="Content-Type" content="text/html; charset=utf-8" />
   <style type="text/css">
body
{
   font: 13px/1.231 arial,helvetica,clean,sans-serif;
   color: #000000;
}

body,div,p
{
   margin: 0;
   padding: 0;
}

div
{
   text-align: center;
}

ul
{
   text-align: left;
}

li
{
   padding: 0.2em;
}

div.panel
{
   display: inline-block;
}
   </style>
   <title>Alfresco &raquo; Link Google Docs&trade; Account</title>
</head>
<body>
   <div>
      <br/>
      <img src="/share/themes/default/images/app-logo.png">
      <br/>
      <br/>
      <#if authenticated>
       <script type="text/javascript">
         if (typeof window.opener.window.Alfresco.GoogleDocs == "object" && typeof window.opener.window.Alfresco.GoogleDocs.onOAuthReturn == "function")
         {
            window.opener.window.Alfresco.GoogleDocs.onOAuthReturn(true);
            self.close();
         }
         else if (typeof window.dialogArguments == "object" && typeof window.dialogArguments.onOAuthReturn == "function")
         {
            window.dialogArguments.onOAuthReturn(true);
            returnValue = {authenticated: true};
            self.close();
         }
         else
         {
            alert("Could not find return function in parent window");
         }
      </script>
      <p style="font-size:150%">Link to Google Docs&trade; Account Complete.</p>
      <br/>
      <p>If the page does not close, clink the link below</p>
      <#else>
      <p style="font-size:150%">Failed to link Google Docs&trade; Account. Please try again at a later time. If the issue continues, please contact your System Administrator.</p>
      </#if>
      <br/>
      <a href="javascript: self.close();">Close page</a>
      <br/>
      <br/>
      <br/>
      <a href="http://www.alfresco.com">Alfresco Software</a> Inc. &copy; 2005-2012 All rights reserved.
   </div>
</div>
</body>
</html>