<#include "header.ftl">
<article>
  <h1>${title!"This page has been deleted"}</h1>
  <p>This page has been deleted.</p>
  <#if deleted?has_content>
  <p class="deleted-reason">${deleted}</p>
  </#if>
</article>
<#include "footer.ftl">
