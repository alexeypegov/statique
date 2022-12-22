# Changelog

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