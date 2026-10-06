# Changelog

All notable changes to Narwhal. Versions follow [Semantic Versioning](https://semver.org).

## [0.3.0] - 2026-10-06

### Added
- New name and icon: the app is now Narwhal (application ID `io.github.yighy.narwhal`), with a
  narwhal launcher icon, themed icon and notification icon, also shown on the welcome screen and
  in the drawer
- Multiple selection in notebooks, as in Orgzly: move, cut, copy, paste, delete, set state,
  scheduled and deadline times; select and delete or rename notebooks
- Note screen: edit tags, state and priority in place; notebook icons; share a note;
  a menu to add notes above, under or below the current one
- Orgzly search syntax, plus `ad.N` (agenda view), `e.` (events), `cr.` (creation date) and the
  `o.e` / `o.cr` sort orders; saved searches with their own screen (select, delete, reorder)
- End times for scheduled and deadline times; repeater symbols (`+`, `++`, `.+`)
- Org links of any kind (`http`, `mailto`, `tel`, `geo`, `file:`…) open as in Orgzly;
  `file:` suggestions from the notes folder; backlinks to the current note
- When a note is renamed, links showing its old title can be updated (ask, always or never)
- Checkboxes that can be ticked; Org markup (bold, italic, code…) rendered
- Typing help: list and checkbox continuation, bracket pairs, and a toolbar over the keyboard
  (list, checkbox, indent, link, date, undo, redo)
- Live reload: files changed by another app are picked up while Narwhal is open
- Reminders: snooze (in 1 hour, tomorrow) from the notification, optional alarm clock alarms
- Option to add a `CREATED` property to new notes
- Theme setting (system, light, dark) and dynamic color
- Signed release builds

### Changed
- Shared element transitions between screens; a swipe cuts a transition short, and each screen
  keeps its scroll position
- Titles, content and the notebook preface save without buttons; back saves the note
- New notes open in the full note screen instead of a dialog
- Joda-Time removed, for a smaller app

## [0.2.0] - 2026-10-04

### Added
- Agenda: overdue tasks, today and the next 6 days, with repeating tasks
- Reminders at scheduled and deadline times, with a Done action, kept across reboots
- Redesigned interface with Material 3 Expressive

## [0.1.0] - 2026-10-04

### Added
- First prototype: notebooks from a folder of `.org` files, outline, note editing, search, tags,
  and `id:` links to other notes

[0.3.0]: https://github.com/yighy/narwhal/compare/v0.2.0...v0.3.0
[0.2.0]: https://github.com/yighy/narwhal/compare/v0.1.0...v0.2.0
[0.1.0]: https://github.com/yighy/narwhal/releases/tag/v0.1.0
