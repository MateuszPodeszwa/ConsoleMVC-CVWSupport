# ConsoleMVC CvwSupport

Editor support for the `.cvw` (Console View) files of the [ConsoleMVC](https://github.com/MateuszPodeszwa/ConsoleMVC) .NET framework:

- a **JetBrains Rider plugin** (this folder's Gradle project, described below), and
- a **VS Code extension** in [`vscode/`](vscode): highlighting, completion, hover documentation and checks for C-View Markup, and a live preview drawn by ConsoleMVC's rendering engine. See its [README](vscode/README.md) and [design](docs/VSCODE_EXTENSION.md).

Both read the markup's schema from ConsoleMVC (`schema/cvw-schema.json`), and draw previews with ConsoleMVC's `cvw-preview` tool (`dotnet tool install -g ConsoleMVC.Preview`).

## VS Code extension

```bash
cd vscode
npm ci
npm test                         # unit, grammar and controller tests
xvfb-run -a npm run test:integration   # in a real VS Code (xvfb-run only on Linux without a display)
npx vsce package --no-dependencies     # consolemvc-cvw-<version>.vsix
```

Install the `.vsix` with **Extensions → … → Install from VSIX**. After a new ConsoleMVC release, run
`npm run sync-schema -- <ConsoleMVC checkout>` to copy its schema and rebuild the grammar, then
`node scripts/compare-with-generator.js` to check the extension's checks against the generator. The
[VS Code extension workflow](.github/workflows/vscode.yml) tests and packages it on every change, and releases it for a
`vscode-vX.Y.Z` tag.

## What is ConsoleMVC?

[ConsoleMVC](https://www.nuget.org/packages/ConsoleMVC.Framework) is a .NET 10 framework that brings ASP.NET Core MVC patterns to console applications. Its `.cvw` views describe screens in C-View Markup, with C# where it is needed, and a bundled Roslyn source generator compiles them into view classes:

```
@model MyApp.Models.HomeViewModel

<box title="@Model.Title" class="panel">
    <text>@Model.Message</text>
</box>

<menu>
    <option key="1" nav="Home/About">About</option>
    <option key="q" quit>Exit</option>
</menu>
```

## Rider plugin features

### C-View Markup
- **Highlighting** of tags, attributes, values, text, the `@` before C#, `@@` escapes, entities and `@* *@` comments, with the C# in conditions, code blocks and `@expressions` highlighted as C#; every colour can be changed under **Settings > Editor > Color Scheme > CVW (Console View)**
- **Completion** from ConsoleMVC's markup schema: after `<` only the elements allowed there (only `<row>` inside a `<table>`, no `<menu>` inside a `<box>`), written with their required attributes and closing tag; the attributes not written yet; an attribute's values inside its quotes; the closing tag after `</`; directives and `@if`, `@foreach`, `@{ }` after `@`. It opens by itself after `<`, `</`, `@`, a quote and a space in a tag
- **Quick documentation** (Ctrl+Q, or hover) for elements, attributes and directives
- **Checks while typing**: with `cvw-preview` installed, the build's own diagnostics (`CVW100`, `CVW105` and the rest); without it, checks against the schema (unknown elements and attributes, placement, required attributes, values, unclosed elements). A page view without a `<menu>` that never returns gets a warning with a quick fix
- **Live preview**: the **ConsoleMVC Preview** tool window (Ctrl+Shift+Alt+V, or **Show ConsoleMVC Preview** in the editor's context menu) draws the selected view with ConsoleMVC's rendering engine while you type. It needs `dotnet tool install -g ConsoleMVC.Preview`; set its path under **Settings > Tools > ConsoleMVC** if it is not on the PATH. It never runs in untrusted projects, and the page blocks scripts
- **Comments** with `@* *@` (Ctrl+/ and Ctrl+Shift+/), which work in markup and C# alike

### Navigation
- Gutter icons on `.cvw` files linking to corresponding controller files
- Gutter icons on `NavigationResult.To("Controller", "Action")` linking to target controllers
- **Go to Related** (Ctrl+Alt+Home) between views and controllers (bidirectional)
- Editor tab titles show `Controller/ActionView` format

### Live Templates
| Abbreviation | Description |
|---|---|
| `cvw` | A view with a model, a box and a menu |
| `cvwform` | Fields posted to an action, with `<errors />` |
| `cvwtable` | A table with a header row and a row per item |
| `navto` | `NavigationResult.To("Controller", "Action")` |
| `navaction` | `NavigationResult.ToAction("Action")` |
| `navquit` | `NavigationResult.Quit()` |

### File Templates
- **New > ConsoleMVC View**: a `.cvw` view with `@model`, a box and a menu

### Structure View and Editor
- Structure view of `@model`, `@using` and `NavigationResult` targets
- Brace matching for `{}`, `()`, `[]`, code folding, smart extend selection (Ctrl+W)
- Model type mismatch detection between a controller's `View()` call and the view's `@model`

### ReSharper Backend (Semantic Analysis)
- A design-time C# document for each view: the view's C# is copied and mapped back to its place, markup is left out, and each `@expression` in markup becomes a statement, so ReSharper checks, completes and navigates the C# of conditions, code blocks and expressions
- The class has the namespace, base class (`ConsoleView<T>`, `ConsolePartialView<T>` or `ConsoleLayoutView`) and model of the one ConsoleMVC's generator writes, in a `.DesignTime` namespace of its own so the two never clash; `@inject` services are properties
- Ctrl+Click on the `@model` type and the C# in the view, and rename refactoring of models, through the mapping

### How it is tested
- `./gradlew test`: the schema, parser, completion, checks and tokeniser; the platform's own lexer checks and the editor's incremental highlighter after edits; and, with `CVW_PREVIEW` set to a `cvw-preview`, the schema checks compared with ConsoleMVC's generator on over 7,000 generated views
- `dotnet test src/dotnet/CvwSupport.Tests`: each template view's design-time document compiled against the ConsoleMVC.Framework package, and compared with what ConsoleMVC's real source generator makes of the same view
- `./gradlew verifyPlugin`: JetBrains' Plugin Verifier against Rider 2025.1

## Requirements

- **JetBrains Rider 2025.1** or later

## Installation

### From JetBrains Marketplace
1. In Rider, go to **Settings > Plugins > Marketplace**
2. Search for "ConsoleMVC CvwSupport"
3. Click **Install** and restart Rider

### From Disk
1. Download the latest release ZIP from [Releases](https://github.com/MateuszPodeszwa/ConsoleMVC-CVWSupport/releases)
2. In Rider, go to **Settings > Plugins > gear icon > Install Plugin from Disk...**
3. Select the ZIP file and restart Rider

## Project Structure

The plugin follows the standard Rider plugin dual architecture:

- **Frontend (Kotlin/IntelliJ)**: UI, file type registration, syntax highlighting, completion, navigation, editor features
- **Backend (C#/ReSharper)**: Deep C# analysis via `GeneratedDocumentServiceBase`, type resolution, refactoring support

```
src/
  main/
    kotlin/     # Frontend plugin code
      markup/   #   schema, parser, completion, checks and documentation of C-View Markup
      lexer/    #   the lexer: markup tokens, then C# tokens
      preview/  #   cvw-preview runner, preview page and tool window
      settings/ #   Settings > Tools > ConsoleMVC
    resources/  # plugin.xml, icons, templates, schema/cvw-schema.json (copied from ConsoleMVC)
  test/         # JUnit tests and testData
  dotnet/
    CvwSupport/       # ReSharper backend (net472)
    CvwSupport.Tests/ # Design-time document tests (dotnet test), not part of the plugin build
vscode/         # The VS Code extension (plain JavaScript)
  src/          #   schema, markup parser, language services, preview, grammar builder
  schema/       #   cvw-schema.json, copied from ConsoleMVC
  syntaxes/     #   cvw.tmLanguage.json, generated from the schema
  test/         #   node:test suites and the VS Code integration tests
```

## Building from Source

**Prerequisites:** JDK 21, .NET SDK, Gradle 9.4+

```bash
# Build the full plugin (frontend + backend)
./gradlew buildPlugin -x buildSearchableOptions

# Output: build/distributions/ConsoleMVC-CvwSupport-<version>.zip

# Run the tests (set CVW_PREVIEW to a cvw-preview to compare the checks with the generator)
./gradlew test
dotnet test src/dotnet/CvwSupport.Tests

# Check the plugin against Rider's APIs
./gradlew verifyPlugin

# Run a sandbox Rider instance with the plugin loaded
./gradlew runIde
```

After a new ConsoleMVC release, copy its `schema/cvw-schema.json` to `src/main/resources/schema/` (and to `vscode/schema/` with
`npm run sync-schema`); the tests show what else must follow, such as the backend's list of elements.

## Related

- [ConsoleMVC](https://github.com/MateuszPodeszwa/ConsoleMVC): the .NET framework these plugins support
- [ConsoleMVC.Framework on NuGet](https://www.nuget.org/packages/ConsoleMVC.Framework): the NuGet package
- [ConsoleMVC.Preview on NuGet](https://www.nuget.org/packages/ConsoleMVC.Preview): the `cvw-preview` tool

## License

MIT
