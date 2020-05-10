<!DOCTYPE html>
<html lang="en">
  <head>
    <#if note?exists>
    <title>${note.title} - ${vars.blog_title}</title>
    <#elseif ndx?exists && ndx gt 1>
    <title>Page ${ndx} - ${vars.blog_title}</title>
    <#else>
    <title>${vars.blog_title}</title>
    </#if>
    <meta charset="utf-8" />
    <meta name="viewport" content="width=device-width, minimum-scale=1, maximum-scale=1" />
    <link rel="index" id="link-index" href="${vars.blog_url}" />
    <#if prev_page?exists>
      <#assign earlier = vars.blog_url + "/" + prev_page + ".html">
    <link rel="prev" id="link-earlier" href="${earlier}" />
    </#if>
    <#if next_page?exists>
      <#assign later = vars.blog_url + "/" + next_page + ".html">
    <link rel="next" id="link-later" href="${later}" />
    </#if>
    <link rel="icon" type="image/png" href="${vars.blog_url}/i/favicon.png">
    <link rel="alternate" type="application/atom+xml" title="Atom feed" href="${vars.feed_url}"/>
  </head>
<body>
<header>
  <h1>Static blog</h1>
  <nav>
    <a href="about.html">About</a>
  </nav>
</header>
<main>
