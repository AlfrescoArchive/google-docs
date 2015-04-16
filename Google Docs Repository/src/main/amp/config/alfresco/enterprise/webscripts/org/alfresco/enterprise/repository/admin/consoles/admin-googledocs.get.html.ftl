<#include "../admin-template.ftl" />

<@page title=msg("googledocs.title")>

<div class="column-full">
    <p class="intro">${msg("googledocs.intro-text")?html}</p>
    <@section label=msg("googledocs.settings.label") />
    <@attrfield attribute=attributes["googledocs.version"] label=msg("googledocs.version.label") />
    <@attrcheckbox attribute=attributes["googledocs.enabled"] label=msg("googledocs.enabled.label") description=msg("googledocs.enabled.description") />
    <@attrtext attribute=attributes["integration.googleDocs.idleThresholdSeconds"] label=msg("integration.googleDocs.idleThresholdSeconds.label") description=msg("integration.googleDocs.idleThresholdSeconds.description") />
</div>

</@page>
