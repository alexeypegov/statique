<?xml version="1.0" encoding="utf-8"?>
<feed xmlns="http://www.w3.org/2005/Atom" xml:base="${base_url}">
  <title>${vars.blog_title}</title>
  <link href="${vars.blog_url}/${name}.xml" rel="self" />
  <link href="${vars.blog_url}" />
  <id>${vars.blog_url}/</id>
  <#assign date = items[0].rfc_3339>
  <updated>${date}</updated>
  <#list items as note>
    <entry>
      <title><![CDATA[${note.title}]]></title>
      <link href="${vars.blog_url}${note.link}" />
      <id>urn:uuid:${note.uuid}</id>
      <updated>${note.rfc_3339}</updated>
      <#if description?exists>
      <content type="xhtml">
        <div xmlns="http://www.w3.org/1999/xhtml">
        ${note.html}
        </div>
      </content>
      <author>
        <name>${vars.feed_author}</name>
        <email>${vars.feed_email}</email>
      </author>
      </#if>
    </entry>
  </#list>
</feed>
