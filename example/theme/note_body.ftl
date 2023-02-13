<#setting locale="ru_RU">
<#setting date_format="dd MMMM yyyy">

<#assign created_at=((note.created_at)!created_at)>  
<#assign datetime=created_at?datetime(vars.datetime_format)>

<article>
  <h1><a href="${note.slug}.html">${note.title}</a></h1>
  <div class="sub">
    <time class="date" datetime="${created_at}">${datetime?date}</time>
    <#-- <div class="tags"> -->
    <#-- <#list note.tags as tag> -->
      <#-- <span class="tag">${tag}</span> -->
    <#-- </#list> -->
    </div>
  </div>
  ${note.body}
</article>