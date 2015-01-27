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
   <title>${msg('auth.title')}</title>
</head>
<body>
   <div>
      <br/>
      <img src="/share/themes/default/images/app-logo.png">
      <br/>
      <br/>
      <#if authenticated>
       <script type="text/javascript">
         if (typeof window.opener.window.Alfresco.GoogleDocs == "object" && typeof window.opener.window.Alfresco.GoogleDocs.onOAuthReturn != "undefined")
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
            alert("${msg('err.noreturn')}");
         }
      </script>
      <p style="font-size:150%">${msg('auth.complete')}</p>
      <br/>
      <p>${msg('auth.close')}</p>
      <#else>
      <p style="font-size:150%">${msg('err.auth')}</p>
      </#if>
      <br/>
      <a href="javascript: self.close();">${msg('link.close')}</a>
      <br/>
      <br/>
      <br/>
      <a href="http://www.alfresco.com">Alfresco Software</a> Inc. &copy; 2005-2015 All rights reserved.
   </div>
</div>
</body>
</html>
