# Statique

Static blog generator written in Clojure

# Features

- Notes and standalone blog pages in [Markdown][1] with [YAML metadata][2]
- [Freemarker][3] templates for rendering notes, standalone pages and feeds ([RSS][4], [Atom][5])
- Suport for rech content like Youtube, Vimeo videos and Flickr photos by their URLs (with [noembed.com](https://noembed.com))
- Incremental rendering of changed notes/pages
- Support of drafts

# Not supported

- Tags
- Static files incremental copy

## Version

0.2.6

## Examples

See `example` directory.

## Usage

Run statique in a directory with `blog.yaml` or pass it as an argument

## Making binary

    lein bin

## License

Copyright © 2018-2020 Alexey Pegov

Distributed under the Eclipse Public License either version 1.0 or (at
your option) any later version.
 
[1]: https://daringfireball.net/projects/markdown/syntax 
[2]: https://assemble.io/docs/YAML-front-matter.html
[3]: https://freemarker.apache.org/
[4]: https://www.rssboard.org/rss-specification
[5]: https://validator.w3.org/feed/docs/atom.html
