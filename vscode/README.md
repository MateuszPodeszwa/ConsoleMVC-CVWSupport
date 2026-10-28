# ConsoleMVC C-View Markup for VS Code

Editor support for the `.cvw` views of [ConsoleMVC](https://github.com/MateuszPodeszwa/ConsoleMVC), the .NET framework
that brings Model-View-Controller to console applications.

- **Highlighting** of elements, attributes, values, `@expressions`, `@if` / `@foreach` blocks, directives and comments,
  with the C# inside views highlighted as C#.
- **Completion** of the elements allowed where you type (only `<row>` inside a `<table>`, no `<menu>` inside a
  `<box>`), their required attributes, the attributes not written yet, the values an attribute accepts, the closing
  tag, directives and code blocks.
- **Documentation** on hover for every element, attribute and directive.
- **Checks while you type**: with `cvw-preview` installed, the build's own diagnostics (`CVW100`, `CVW105` and the
  rest); without it, checks against the markup's schema.
- **Live preview** of the view, drawn by ConsoleMVC's real rendering engine: **ConsoleMVC: Open Preview to the Side**
  (`Ctrl+K V`, `Cmd+K V` on macOS), or the preview button in the editor's title bar.
- **Snippets**: `cvw` (a view), `cvw-form`, `cvw-layout`, `cvw-table` and `cvw-columns`.

## The preview

The preview and the build's checks come from the `cvw-preview` tool of ConsoleMVC 1.34 or later. Install it once:

```bash
dotnet tool install -g ConsoleMVC.Preview
```

The preview follows your typing, including unsaved changes. It reads markup without running any C#: expressions show
as placeholders such as `{Model.Title}`, loops are drawn once, and the layout and partial views come from
`Views/Shared`. See the [Editor Support guide](https://github.com/MateuszPodeszwa/ConsoleMVC/blob/main/docs/guides/docs/editor-support.md)
for what it shows.

## Settings

| Setting | Default | Meaning |
|---|---|---|
| `consolemvc.preview.path` | `cvw-preview` | The tool's command or full path. It can be set only in user settings, so a folder you open cannot make the extension run another program. When it is the default, `~/.dotnet/tools` is searched too |
| `consolemvc.preview.width` | `80` | The width of the preview in columns (20 to 500) |
| `consolemvc.preview.highContrast` | `false` | Draws with ConsoleMVC's high-contrast theme |
| `consolemvc.preview.delay` | `300` | Milliseconds to wait after typing stops before updating the preview and checks |
| `consolemvc.diagnostics.enabled` | `true` | Checks views while they are typed |

## Security

- In [untrusted folders](https://code.visualstudio.com/docs/editor/workspace-trust) the tool never runs; highlighting,
  completion and the schema checks still work.
- The tool is started without a shell, with its arguments as a list, and receives the view through standard input. It
  runs no code from the project and reads only the view's shared views.
- The preview panel runs no scripts, and its Content Security Policy allows only inline styles. Only the tool's screen
  (text and coloured spans) is shown; anything else is escaped.

## Requirements

VS Code 1.90 or later. The preview needs the .NET SDK and the `ConsoleMVC.Preview` tool.

## Licence

[MIT](LICENSE), by Mateusz Podeszwa.
