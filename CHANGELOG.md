# Changelog

## 0.5.4

### Removed

- 'created_at' is not passed as an item property to Freemarker templates, all metadata fields are passed as is.
- 'tz' & 'date-format' config properties were removed as well

### Fixed

- A bug preventing feed/page to be rerendered in a case if one of its items has been changed
- A bug that ignores 'Updated' metadata field if 'Date' field was declared for the item while building sitemap.xml 

## 0.5.3

### Added

- Sitemap generation, see `examples`

## 0.5.2

### Changed

- Performance: got rid of `javax.imageio` in favor of own image dimension reader (`statique.image`)

## 0.5.1

### Fixed

- Page rendering bug

## 0.5.0

### Changed

- Completely rewritten using more ideomatic Clojure and totally avoiding global state
- Caches were reduced to one items.edn
- Draft note property isn't supported anymore
- Feed templates now rendered separately and `item.body` is passed in instead of `item.body_abs`
- Project dependencies were updated to most recent ones

## 0.4.5

### Changed

- To improve post layouting, image files now rendered into "img" tags with "width" and "height" attributes being specified

## 0.4.4

### Added

### Changed

- formatted date variables (`rfc_3339` and `rfc_882`) has been replaced with `created_at` of [ISO_OFFSET_DATE_TIME](https://docs.oracle.com/javase/8/docs/api/java/time/format/DateTimeFormatter.html#ISO_OFFSET_DATE_TIME) format. New `vars.datetime_format` variable has been added for easily parsing `created_at` in FreeMarker

## 0.4.3

### Added

- Single pages now use same markdown-extensions as regular posts (so, all things like "@2x" should also work)

## 0.4.2

### Added

- Ability to use @2x images in posts (image name should contain "@2x")

## 0.4.1

### Added

- While rendering an image, if it name contains "@2x", it will be rendered as an img tag with width set to "50%".
- Adding "loading=lazy" to all Markdown images

## 0.4.0

### Added

- skip-notes, skip-pages, skip-singles, skip-feeds config options of boolean type (default: false) to skip file generation of particular type

## 0.3.3

### Fixed

- Draft notes are now processed properly and treated as non-draft if "Draft: false" (plus draft notes are reported now)

## 0.3.2

### Fixed

- Skipping caches should still write them if changed

## 0.3.1

### Changed

- Some caches won't be written if weren't changed

## 0.3.0

### Added 

- Ability to skip certain caches during generation by defining java property 'skip-{name}-cache=true', where {name} is 'notes', 'pages', 'singles', 'noembed'

### Changed

- note properties are injected directly (i.e. not wrapped into an object) into 'note' template

## 0.2.9

### Added

- Added `note.prev` and `note.next` vars in note template

## 0.2.8

### Added

- Setting `notes-per-page` option to 0 will skip pages generation completely
- `items-per-feed` general configuration option (with default set to 10 items)

### Changed

- note, page & feed caches has been splitted into separate files

### Fixed

- `note-template` can't be overriden in blog config