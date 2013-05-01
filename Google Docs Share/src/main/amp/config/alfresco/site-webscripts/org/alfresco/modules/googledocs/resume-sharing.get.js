<import resource="classpath:/alfresco/templates/org/alfresco/import/alfresco-util.js">

/**
 * Return the domain part of an email address
 * @param email
 * @returns
 */
function emailDomain(email)
{
   if (email.indexOf("@") > 0)
   {
      return email.split("@")[1];
   }
   else
   {
      return null;
   }
}


/**
 * Compare the email domains of two addresses. Return 1 if they are the same, zero otherwise.
 * @param email
 * @returns
 */
function compareEmailDomains(email1, email2)
{
   var domain1 = emailDomain(email1), domain2 = emailDomain(email2);
   return (domain1 !== null && domain2 !== null && domain1 === domain2) ? 1 : 0;
}

function compareAlphabetical(text1, text2)
{
   if (text1 < text2)
   {
      return -1;
   }
   else if (text1 > text2)
   {
      return 1;
   }
   else
   {
      return 0;
   }
}

function main()
{
   AlfrescoUtil.param('nodeRef');
   AlfrescoUtil.param('site', null);

   var metadata = AlfrescoUtil.getNodeDetails(model.nodeRef, model.site),
      showWarning = false;

   if (metadata)
   {
      var permissions = [], permissionStrs = metadata.item.node.properties["gd2:permissions"];
      if (permissionStrs != null)
      {
         for (var i = 0; i < permissionStrs.length; i++)
         {
            var parts = permissionStrs[i].split("|");
            if (parts.length === 3)
            {
               var permissionObj = {
                     authorityType: parts[0],
                     authorityId: parts[1],
                     roleName: parts[2],
                     highlighted: false,
                     selected: true
               };
               permissions.push(permissionObj);
            }
         }
      }

      if (permissions.length > 0)
      {
         // Custom web script to get info about the current user's identity from Google
         var profileResp = remote.call("/googledocs/userprofile");
         if (profileResp.status.code === 200)
         {
            var json = jsonUtils.toObject(profileResp.response);
            if (json.email)
            {
               var email = json.email,
                  numEvil = 0; // Number of users who have a different domain to the current user
               for (var j = 0; j < permissions.length; j++)
               {
                  var authorityType = permissions[j].authorityType,
                     authorityId = permissions[j].authorityId;
                  if (
                        authorityType === "anyone" ||
                        authorityType === "domain" && authorityId !== emailDomain(email) ||
                        (authorityType === "user" || authorityType === "group") && compareEmailDomains(authorityId, email) == 0 // Domains are different
                     )
                  {
                     permissions[j].selected = false;
                     permissions[j].highlighted = true;
                     numEvil ++;
                  }
               }

               // Display a warning to the user if there are users from other domains
               showWarning = numEvil > 0;

               // Sort the permissions so that they appear with all external users first, in alphabetical order by authorityId,
               // followed by all internal users, in alphabetical order
               permissions.sort(function(p1, p2) {
                  if (p1.highlighted && !p2.highlighted)
                  {
                     return -1;
                  }
                  else if (!p1.highlighted && p2.highlighted)
                  {
                     return 1;
                  }
                  else
                  {
                     return compareAlphabetical(p1.authorityId, p2.authorityId);
                  }
               });
            }
         }
      }

      model.permissions = permissions;
   }

   model.showWarning = showWarning;
}

main();