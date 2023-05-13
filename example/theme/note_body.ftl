<#setting locale="ru_RU">
<#setting date_format="dd MMMM yyyy">

<#assign title=(note.title)!title>
<#assign slug=(note.slug)!slug>
<#assign tags=(note.tags)!tags>
<#assign body=(note.body)!body>
<#assign created_at=((note.created_at)!created_at)>

<#assign datetime=created_at?datetime(vars.datetime_format)>

<article>
  <h1><a href="${slug}.html">${title}</a></h1>
  <div class="sub">
    <time class="date" datetime="${created_at}">${datetime?date}</time>
    <#-- <div class="tags"> -->
    <#-- <#list tags as tag> -->
      <#-- <span class="tag">${tag}</span> -->
    <#-- </#list> -->
    </div>
  </div>
  ${body}
</article>