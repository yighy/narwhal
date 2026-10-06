# Narwhal

A lightweight Android app for [Org mode](https://orgmode.org) notes, in the spirit of
[Orgzly](https://github.com/orgzly/orgzly-android), with a list-based interface.

Your `.org` files are the only storage: Narwhal reads and writes them in place, in a folder you
pick (for example one synced with Syncthing). Untouched parts of a file are written back byte for
byte, so other apps (Emacs, Orgzly, Orgro…) see clean changes.

## Features

### Notebooks and notes
- Notebooks (one per `.org` file, in the folder and its subfolders) shown as cards, with an
  optional emoji icon; create, rename and delete them
- Notebook outline with animated folding, the file's preface on top (edited in place), optional
  note content
- Swipe a note right to toggle TODO / DONE (with Org semantics: CLOSED time, repeating tasks
  move forward), swipe left for actions (add a sub-note or a note after)
- Multiple selection, as in Orgzly: move up, down, in or out, to another notebook; cut, copy,
  paste; set state, scheduled and deadline times; delete
- Note screen: title, content and metadata edited in place, without dialogs (tags, state and
  priority as chips); scheduled and deadline times with end times and repeaters (`+`, `++`,
  `.+`); properties; a menu to add notes above, under or below; share a note
- `ID` (on by default, hidden on the note screen unless asked) and optional `CREATED` properties
  on new notes

### Writing
- Typing help: lists and checkboxes continue on Enter, brackets are paired
- A toolbar over the keyboard: bold, italic, underline, strike-through, code, list, checkbox,
  indent, outdent, link, date (with time, end time and repeater), undo, redo
- Org markup (`*bold*`, `/italic/`, `=code=`…) rendered, checkboxes ticked with a tap
- Org links of any kind open as in Orgzly (`http`, `mailto`, `tel`, `geo`, `file:`…), with
  `file:` suggestions from the notes folder
- `id:` linking: type `id:` to pick another note by title; Narwhal adds an `ID` property to that
  note if needed and inserts `[[id:…][Title]]`; backlinks list the notes that link to the
  current one; links showing a renamed note's old title can be updated

### Search and agenda
- Orgzly search syntax (`s.today`, `t.work`, `i.todo`, `o.p`…) plus `ad.N` (agenda view), `e.`
  (events), `cr.` (creation date) and the `o.e` / `o.cr` sort orders
- Saved searches with their own screen: select, delete, reorder
- Agenda: overdue tasks, then today and the next 6 days from scheduled and deadline times and
  events, including each occurrence of repeating tasks

### Reminders
- At scheduled and deadline times, optionally for dates without a time
- Notification actions: Done, snooze (5 minutes to 3 hours, set in the settings), tomorrow
- Exact alarms, optionally as alarm clock alarms (most reliable with battery saving),
  rescheduled after a reboot, an update or a clock change; missed reminders shown after the
  phone was off

### Files
- Files changed by another app are picked up while Narwhal is open, and a note changed on disk
  is never overwritten: the save is refused and the note reloaded
- Pull down to reload

### Look
- Material 3 Expressive design (uses the material3 1.5 alpha), shared element transitions
- Light, dark or system theme, dynamic color from the wallpaper (Android 12+)
- Comfortable, compact or dense spacing between notes

## Download

Signed APKs are attached to each [release](https://github.com/yighy/narwhal/releases); see
[CHANGELOG.md](CHANGELOG.md) for what changed.

## Build

Requires JDK 17+ and the Android SDK (platform 37).

```bash
./gradlew :app:testDebugUnitTest :app:assembleDebug
```

Release builds are signed when a `keystore.properties` file at the project root (kept out of
git) gives `storeFile`, `storePassword`, `keyAlias` and `keyPassword`. The APK is named after
the version, for example `app/build/outputs/apk/release/narwhal-0.3.0-release.apk`.

## License

GPLv3. Includes a copy of [org-java](https://github.com/orgzly/org-java) (GPLv3), see
[org-java/README.md](org-java/README.md).
