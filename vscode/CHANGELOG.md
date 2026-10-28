# Changelog

## [0.1.0]

### Added

- The `cvw` language for `.cvw` files, with a grammar generated from ConsoleMVC's markup schema: elements,
  attributes, values, `@expressions`, code blocks, directives, comments and entities, with embedded C#.
- Completion of elements (only where they are allowed), required attributes, attributes, values, closing tags,
  directives and code constructs; documentation on hover.
- Checks while typing: the build's diagnostics from `cvw-preview`, or checks against the schema without it.
- A live preview panel drawn by `cvw-preview` (ConsoleMVC.Preview 1.34 or later), updated while typing.
- Snippets for a view, a form, a layout, a table and columns.
- Settings for the tool's path, the preview's width, the high-contrast theme, the delay and the checks.
