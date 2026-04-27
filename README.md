# Statique

Static site/blog generator written in Clojure.

Statique turns Markdown files with YAML front matter into HTML using FreeMarker templates. It is designed for blogs and small static sites where content lives in a directory tree and generated output is written to `out/`.

## Example

- [Ложное движение][ld] — my own blog (in Russian)
- See the `example/` directory for a minimal site layout and configuration.

## Features

- Notes and standalone pages in [Markdown][md] with [YAML front matter][yaml]
- [FreeMarker][fm] templates for notes, pages, standalone pages, feeds, deleted notes, and sitemap output
- RSS and Atom feed generation
- Incremental rendering with an items cache
- Markdown image rendering with `loading="lazy"` and automatic `width` / `height` attributes
- Built-in image dimension reader for PNG, JPEG, GIF, WebP, and AVIF
- Retina image support with the `@2x` filename postfix
- YouTube, Vimeo, and Coub embeds using [noembed][noembed]
- Draft notes via the `Draft` front matter field
- Deleted notes via the `Deleted` front matter field and `deleted.ftl` template
- `sitemap.xml` generation
- Static file and directory copying

## Not Yet Supported

- Tags
- Incremental static file copying

## Version

0.66

### What's New in 0.66

- AVIF image dimensions are now supported by the built-in size reader.
- `sitemap.xml` is regenerated only for sitemap-relevant changes and is not rewritten when rendered content is unchanged.

## Usage

Run Statique in a directory containing `blog.yaml`:

```bash
lein run
```

Or build and run the standalone jar:

```bash
lein uberjar
java -jar target/statique-0.66-standalone.jar
```

### Command Line Options

- `-d`, `--debug` - enable debug output
- `-n`, `--no-cache` - ignore the items cache and force regeneration
- `-c`, `--config PATH` - use a custom config file path, default: `blog.yaml`
- `-h`, `--help` - show help

## Project Layout

Typical site layout:

```text
blog.yaml
notes/
  2026-04-27-example-note.md
singles/
  about.md
theme/
  note.ftl
  page.ftl
  single.ftl
  deleted.ftl
  atom.ftl
  sitemap.ftl
static/
out/
cache/
```

Notes use `YYYY-MM-DD-slug.md` filenames. Files in `notes/` that do not match that pattern are treated as drafts and are not rendered.

## Configuration

The default config file is `blog.yaml`. See [example/blog.yaml](example/blog.yaml) for a complete sample.

Common `general` options:

- `notes-dir`, `singles-dir`, `theme-dir`, `output-dir`, `cache-dir`
- `notes-per-page` and `items-per-feed`
- `feeds`, for example `atom` or `rss`
- `sitemap-template`, disabled by default
- `copy`, a list of static files or directories to copy into the output directory
- `copy-last-as-index`, useful for pageless blogs
- `first-page-as-index`, controls whether the first page is written as `index.html`

Custom `vars` are passed to FreeMarker templates as `vars`; dashes in variable names are converted to underscores.

## Development

Build:

```bash
lein uberjar
```

Run tests:

```bash
lein eftest
```

Standard Leiningen tests also work:

```bash
lein test
```

Start a REPL:

```bash
lein repl
```

## License

Copyright (c) 2018-2026 Alexey Pegov

Distributed under the Eclipse Public License 2.0 or the GNU General Public License 2.0 or later, with Classpath exception, as declared in `project.clj`.

[md]: https://daringfireball.net/projects/markdown/syntax
[yaml]: https://yaml.org/spec/
[fm]: https://freemarker.apache.org/
[rss]: https://www.rssboard.org/rss-specification
[atom]: https://validator.w3.org/feed/docs/atom.html
[noembed]: https://noembed.com
[own]: https://github.com/alexeypegov/pegov.io
[ld]: https://fm.alexeypegov.com
