# Statique

Static site/blog generator written in Clojure

## Features

- Posts and standalone pages in [Markdown][md] with [YAML metadata][yaml]
- [Freemarker][3] templates for rendering posts, standalone pages and feeds ([RSS][rss], [Atom][atom])
- Support for retina-enabled images (with `@2x` postfix)
- Embedding Youtube and Vimeo videos using [noembed][noembed]
- Incremental rendering of changed posts/pages
- sitemap.xml support

## Not [yet] supported

- Tags
- Static files incremental copy

## Version

0.61

## Examples

See `example` directory or [my own blog][own]

## Usage

Run `statique` in a directory with `blog.yaml`

### Command Line Options

- `-d, --debug` - Enable debug output
- `-n, --no-cache` - Ignore items cache (force regeneration)  
- `-c, --config PATH` - Path to config file (default: blog.yaml)
- `-h, --help` - Show help

## Make

    lein uberjar

## License

Copyright © 2018-2025 Alexey Pegov

Distributed under the Eclipse Public License either version 1.0 or (at
your option) any later version.
 
[md]: https://daringfireball.net/projects/markdown/syntax 
[yaml]: https://assemble.io/docs/YAML-front-matter.html
[fm]: https://freemarker.apache.org/
[rss]: https://www.rssboard.org/rss-specification
[atom]: https://validator.w3.org/feed/docs/atom.html
[noembed]: https://noembed.com
[own]: https://github.com/alexeypegov/pegov.io
