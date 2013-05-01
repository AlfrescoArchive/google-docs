<#escape x as jsonUtils.encodeJSONString(x)>
{
    "authenticated": ${authenticated?string},
    "id": "${id!""}",
    "name": "${name!""}",
    "firstName": "${firstName!""}",
    "lastName": "${lastName!""}",
    "email": "${email!""}"
}
</#escape>