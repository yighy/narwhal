# Changelog

All notable changes to Narwhal. Versions follow [Semantic Versioning](https://semver.org).

## [Unreleased]

## [0.7.1] - 2026-10-08

### Changed
- Notebook initials and icons sit in a plain circle, of the same color for all notebooks

### Fixed
- In a done note's title, links are grayed out like the rest

## [0.7.0] - 2026-10-08

### Added
- Setting to strike through done notes or not

### Fixed
- In a done note's title, links and underlined text are struck through too

## [0.6.0] - 2026-10-07

### Added
- French translation, and a "Use English language" setting that ignores the phone's language
  (on Android 13+, the app's language can also be chosen in the system settings)
- Moved notes slide to their new place in the outline, and the list scrolls to keep them in view

## [0.5.3] - 2026-10-07

### Changed
- New icon: a round narwhal head with its tusk, without the sea; the themed icon has the same drawing
- Swiping a note left shows plain icons, as swiping right does

### Fixed
- A save can no longer leave the end of a longer previous version at the end of the file; each
  file is read back after it is written

## [0.5.2] - 2026-10-07

### Changed
- No property is hidden by default: the ID shows on the note screen unless added to the hidden
  properties

## [0.5.1] - 2026-10-07

### Changed
- Themed icon (Android 13+): the narwhal is centered

## [0.5.0] - 2026-10-07

### Added
- Hidden properties setting: the properties not shown on the note screen (the ID by default),
  in place of the switch for the ID

### Changed
- New notes no longer get an ID by default: linking to a note gives it one
- New icon: the narwhal rises from the sea with its tail, its tusk on its head

### Fixed
- The list no longer jumps when a note or a notebook gets selected
- The keyboard toolbar goes away with the keyboard
- On a new note, going from the title to the content keeps the keyboard up

## [0.4.0] - 2026-10-06

### Added
- Formatting buttons in the keyboard toolbar: bold, italic, underline, strike-through, code
- The toolbar's date button opens the full date dialog (time, end time, repeater), and changes
  the date at the cursor when there is one
- Snooze length setting for the reminder notification's button
- About section in the settings: version, source code, license, credits
- Space between notes setting for the notebook outline: comfortable, compact or dense
- Setting to show the ID property on the note screen

### Changed
- Undo and redo stay in view at the end of the keyboard toolbar
- The drawer shows the app name without the icon
- The ID property is hidden on the note screen by default
- New notes get an ID by default, so they can be linked to right away
- Notebooks show each note's content under its title by default
- Folding and unfolding notes takes half the time, the notes coming one after the other

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

[Unreleased]: https://github.com/yighy/narwhal/compare/v0.7.1...HEAD
[0.7.1]: https://github.com/yighy/narwhal/compare/v0.7.0...v0.7.1
[0.7.0]: https://github.com/yighy/narwhal/compare/v0.6.0...v0.7.0
[0.6.0]: https://github.com/yighy/narwhal/compare/v0.5.3...v0.6.0
[0.5.3]: https://github.com/yighy/narwhal/compare/v0.5.2...v0.5.3
[0.5.2]: https://github.com/yighy/narwhal/compare/v0.5.1...v0.5.2
[0.5.1]: https://github.com/yighy/narwhal/compare/v0.5.0...v0.5.1
[0.5.0]: https://github.com/yighy/narwhal/compare/v0.4.0...v0.5.0
[0.4.0]: https://github.com/yighy/narwhal/compare/v0.3.0...v0.4.0
[0.3.0]: https://github.com/yighy/narwhal/compare/v0.2.0...v0.3.0
[0.2.0]: https://github.com/yighy/narwhal/compare/v0.1.0...v0.2.0
[0.1.0]: https://github.com/yighy/narwhal/releases/tag/v0.1.0
