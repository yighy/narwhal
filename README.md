# Orgline

A lightweight Android app for [Org mode](https://orgmode.org) notes, in the spirit of
[Orgzly](https://github.com/orgzly/orgzly-android), with a list-based interface.

Your `.org` files are the only storage: Orgline reads and writes them in place, in a folder you
pick (for example one synced with Syncthing). Untouched parts of a file are written back byte for
byte, so other apps (Emacs, Orgzly, Orgro…) see clean changes.

## Features (v0.1)

- Notes from every `.org` file in the folder and its subfolders, grouped by file
- Search (title, content, tags) and tag filter
- View and edit a note: state, title, tags, properties, content
- `id:` linking: type `id:` in a title or content to pick another note by title; Orgline adds an
  `ID` property to that note if needed and inserts `[[id:…][Title]]`
- Tap an `id:` link to open the target note
- Notes changed by another app are never overwritten: the save is refused and the note reloaded

Planned: agenda and reminders.

## Build

Requires JDK 17+ and the Android SDK.

```bash
./gradlew :app:testDebugUnitTest :app:assembleDebug
```

## License

GPLv3. Includes a copy of [org-java](https://github.com/orgzly/org-java) (GPLv3), see
[org-java/README.md](org-java/README.md).
