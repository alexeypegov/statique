<article>
  <h1><a href="${note.slug}.html">${note.title}</a></h1>
  <div class="sub">
    <time class="date" datetime="${note.date}">${note.date}</time>
    <div class="tags">
    <#list note.tags as tag>
      <span class="tag">${tag}</span>
    </#list>
    </div>
  </div>
  ${note.body}
</article>