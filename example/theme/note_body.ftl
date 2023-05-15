<#setting locale="ru_RU">
<#setting date_format="dd MMMM yyyy">

<#assign title=(note.title)!title>
<#assign slug=(note.slug)!slug>
<#assign tags=(note.tags)!tags>
<#assign body=(note.body)!body>
<#assign date=(note.date)!date>

<#assign parsed_date=date?date("yyyy-MM-dd")>

<article>
  <h1><a href="${slug}.html">${title}</a></h1>
  <div class="sub">
    <time class="date" datetime="${parsed_date?string["yyyy-MM-dd'T00:00:00+00:00'"]}">${parsed_date?date}</time>
    <#-- <div class="tags"> -->
    <#-- <#list tags as tag> -->
      <#-- <span class="tag">${tag}</span> -->
    <#-- </#list> -->
    </div>
  </div>
  ${body}
</article>