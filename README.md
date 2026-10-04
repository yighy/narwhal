# Orgline

A lightweight Android app for [Org mode](https://orgmode.org) notes, in the spirit of
[Orgzly](https://github.com/orgzly/orgzly-android), with a list-based interface.

Your `.org` files are the only storage: Orgline reads and writes them in place, in a folder you
pick (for example one synced with Syncthing). Untouched parts of a file are written back byte for
byte, so other apps (Emacs, Orgzly, Orgro…) see clean changes.

## Features

- Notebooks (one per `.org` file, in the folder and its subfolders) shown as cards
- Notebook outline with animated folding, the file's preface on top, optional note content
- Swipe a note right to toggle TODO / DONE (with Org semantics: CLOSED time, repeating tasks
  move forward), swipe left for actions (add a sub-note or a note after); long press for the same
- Pull down to reload
- Note screen editable on tap: title, tags, state, priority, scheduled and deadline times,
  properties, content; only the fields that are set are shown
- Search (title, content, tags) and tag filter, globally or within a notebook
- `id:` linking: type `id:` in a title or content to pick another note by title; Orgline adds an
  `ID` property to that note if needed and inserts `[[id:…][Title]]`
- Tap an `id:` link to open the target note; copy a link to the current note
- Notes changed by another app are never overwritten: the save is refused and the note reloaded
- Agenda: overdue tasks, then today and the next 6 days from SCHEDULED and DEADLINE times,
  including each occurrence of repeating tasks; swipe right to toggle TODO / DONE
- Reminders at scheduled and deadline times (optionally for dates without a time), with a Done
  action; exact alarms, rescheduled after a reboot, an update or a clock change, and missed
  reminders shown after the phone was off
- Material 3 Expressive design (uses the material3 1.5 alpha)


## Build

Requires JDK 17+ and the Android SDK (platform 37).

```bash
./gradlew :app:testDebugUnitTest :app:assembleDebug
```

## License

GPLv3. Includes a copy of [org-java](https://github.com/orgzly/org-java) (GPLv3), see
[org-java/README.md](org-java/README.md).
