<#escape x as jsonUtils.encodeJSONString(x)>
{
    "authenticated": ${authenticated?string},
    "authURL": "${authURL!""}",
    "permissions": <#if permissions??>
    [<#list permissions as p>
        {
            "authorityType": "${p.authorityType}",
            "authorityId": "${p.authorityId}",
            "roleName": "${p.roleName}"
        }<#if p_has_next>,</#if>
    </#list>]<#else>null</#if>
}
</#escape>