<#include "header.ftl">
<#list items as note>
  <#include "note_body.ftl">
</#list>
<nav class="paging">
<#if prev_page?exists>
<a id="prev" href="${prev_page}">older</a>
<#else>
<a nohref>older</a>
</#if>
&#151;
<#if next_page?exists>
<a id="next" href="${next_page}">earlier</a>
<#else>
<a id="next" nohref>earlier</a>
</#if>
</nav>
<#include "footer.ftl">
