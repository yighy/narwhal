# Narwhal

An Android app for [Org mode](https://orgmode.org) notes, inspired by
[Orgzly](https://github.com/orgzly/orgzly-android).

## Features

- **Your files as the only storage.** Narwhal reads and writes the `.org` files of a folder you
  pick (for example one synced with Syncthing). Changes made elsewhere show up within seconds,
  untouched text is written back byte for byte, and a note changed on disk is never overwritten.
- **Notes that link to each other.** Type `id:` to pick a note by its title; Narwhal gives it an
  `ID` if needed and inserts the link. Each note lists the notes that link to it, and links follow
  when a note is renamed.
- **Edit where you read.** Title, content, tags, state and priority are edited in place, without
  dialogs. Lists and checkboxes continue as you type, and brackets close themselves.
- **A toolbar over the keyboard.** Bold, italic, underline, strike-through and code; lists,
  checkboxes, indent and outdent; a link to another note; a date with its time, end time and
  repeat, or a change to the date at the cursor; undo and redo, always in view.
- **Outline, agenda and search.** Notebooks as an outline that folds, swipe a note to mark it
  done, multiple selection to move, cut, copy and paste notes; an agenda of the week with
  repeating tasks; search with Orgzly's syntax (`s.today`, `t.work`, `i.todo`…) and saved
  searches; Org links of any kind (web, mail, phone, maps, files).
- **Reminders.** Exact alarms (optionally as alarm clock alarms), kept across reboots, with Done
  and snooze buttons in the notification.
- **Material 3 Expressive.** Smooth transitions, light and dark themes, colors from your
  wallpaper.

## Download

Signed APKs are attached to each [release](https://github.com/yighy/narwhal/releases); see
[CHANGELOG.md](CHANGELOG.md) for what changed.

## Build

Requires JDK 17+ and the Android SDK (platform 37).

```bash
./gradlew :app:testDebugUnitTest :app:assembleDebug
```

Release builds are signed when a `keystore.properties` file at the project root (kept out of
git) gives `storeFile`, `storePassword`, `keyAlias` and `keyPassword`.

## License

GPLv3. Includes a copy of [org-java](https://github.com/orgzly/org-java) (GPLv3), see
[org-java/README.md](org-java/README.md).
