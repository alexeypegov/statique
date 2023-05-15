<#setting date_format="yyyy-MM-dd'T00:00:00+00:00'">
<?xml version="1.0" encoding="utf-8"?>
<feed xmlns="http://www.w3.org/2005/Atom" xml:base="${base_url}">
  <title>${vars.blog_title}</title>
  <link href="${vars.blog_url}/${name}.xml" rel="self" />
  <link href="${vars.blog_url}" />
  <id>${vars.blog_url}/</id>
  <#assign date = items[0].date>
  <updated>${date?date("yyyy-MM-dd")?date}</updated>
  <#list items as note>
    <entry>
      <title><![CDATA[${note.title}]]></title>
      <link href="${vars.blog_url}${note.link}" />
      <id>urn:uuid:${note.uuid}</id>
      <updated>${note.date?date("yyyy-MM-dd")?date}</updated>
      <content type="xhtml">
        <div xmlns="http://www.w3.org/1999/xhtml">
        ${note.body}
        </div>
      </content>
      <author>
        <name>${vars.feed_author}</name>
        <email>${vars.feed_email}</email>
      </author>
    </entry>
  </#list>
</feed>
