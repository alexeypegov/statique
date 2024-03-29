<?xml version="1.0" encoding="UTF-8"?>
<urlset xmlns="http://www.sitemaps.org/schemas/sitemap/0.9">
  <url>
    <loc>http://www.example.com/</loc>
    <lastmod>${.now?string["yyyy-MM-dd"]}</lastmod>
    <changefreq>monthly</changefreq>
    <priority>0.8</priority>
  </url>
<#list items as item>
  <url>
    <loc>${item.loc}</loc>
    <lastmod>${(item.updated)!(item.date)}</lastmod>
  </url>
</#list>
</urlset>