# Statique

Static site/blog generator written in Clojure

## Features

- Posts and standalone pages in [Markdown][1] with [YAML metadata][2]
- [Freemarker][3] templates for rendering posts, standalone pages and feeds ([RSS][4], [Atom][5])
- Embedding Youtube and Vimeo videos using [noembed][6]
- Incremental rendering of changed posts/pages

## Not [yet] supported

- Tags
- Static files incremental copy
- Sitemap

## Version

0.5.1

## Examples

See `example` directory.

## Usage

Run `statique` in a directory with `blog.yaml`

## Make

    lein uberjar

## License

Copyright © 2018-2023 Alexey Pegov

Distributed under the Eclipse Public License either version 1.0 or (at
your option) any later version.
 
[1]: https://daringfireball.net/projects/markdown/syntax 
[2]: https://assemble.io/docs/YAML-front-matter.html
[3]: https://freemarker.apache.org/
[4]: https://www.rssboard.org/rss-specification
[5]: https://validator.w3.org/feed/docs/atom.html
[6]: https://noembed.com