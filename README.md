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

[ConsoleMVC](https://www.nuget.org/packages/ConsoleMVC.Framework) is a .NET 10.0 framework that brings ASP.NET Core MVC patterns to console applications. It uses `.cvw` view files, plain C# with a small directive preamble, that get compiled into `ConsoleView<TModel>` subclasses by a bundled Roslyn source generator.

```
@model MyApp.Models.HomeViewModel

Console.WriteLine($"=== {Model.Title} ===");
Console.WriteLine(Model.Message);

return input switch
{
    "1" => NavigationResult.To("Home", "About"),
    "2" => NavigationResult.Quit(),
    _ => NavigationResult.To("Home", "Index")
};
```

This plugin makes editing `.cvw` files feel as natural as editing `.cs` files inside Rider.

## Features

### Syntax Highlighting
- Directive keywords (`@model`, `@using`) highlighted as keywords
- Directive arguments highlighted as type/namespace references
- Full C# token-level highlighting in the code body (keywords, strings, numbers, comments, operators)
- Customizable colors via **Settings > Editor > Color Scheme > CVW**

### Code Completion
- `@model` and `@using` directive completion at line starts
- `NavigationResult.To()`, `.ToAction()`, `.Quit()` completions
- `Console.*`, `Model`, `ViewData`, and `return` keyword completions
- Cursor placement inside parentheses and quotes after insertion

### Error Highlighting & Quick-Fixes
- Missing `@model` directive warning (with quick-fix to add it)
- Empty directive arguments flagged as errors
- Missing `return` statement warning (with quick-fix to add `return NavigationResult.Quit()`)
- Incomplete directives (e.g., bare `@model` without a space) flagged as errors
- Cross-file model type mismatch detection (controller `View()` call vs `@model` directive)

### Navigation
- Gutter icons on `.cvw` files linking to corresponding controller files
- Gutter icons on `NavigationResult.To("Controller", "Action")` linking to target controllers
- **Go to Related** (Ctrl+Alt+Home) between views and controllers (bidirectional)
- Editor tab titles show `Controller/ActionView` format

### Live Templates
| Abbreviation | Description |
|---|---|
| `cvw` | Full `.cvw` file scaffold with `@model` and `return` |
| `navto` | `NavigationResult.To("Controller", "Action")` |
| `navaction` | `NavigationResult.ToAction("Action")` |
| `navquit` | `NavigationResult.Quit()` |

### File Templates
- **New > ConsoleMVC View**: creates a `.cvw` file with `@model` scaffold

### Structure View
- Shows `@model` type, `@using` imports, and `NavigationResult` navigation targets

### Additional Editor Features
- Brace matching for `{}`, `()`, `[]`
- Comment toggling (`//` and `/* */`)
- Code folding for directive preamble, brace blocks, and multi-line comments
- Smart extend selection (Ctrl+W) for directives and code body
- Quick documentation (hover) for directives and framework types

### ReSharper Backend (Semantic Analysis)
- Generated document service that creates a virtual C# document mirroring the source generator output
- Bidirectional offset mapping between `.cvw` source and generated C# positions
- Enables Ctrl+Click on `@model` type to navigate to CLR type definitions
- Full semantic C# completion in the code body
- Type-aware error checking (unresolved references, type mismatches)
- Rename/move refactoring propagation to `.cvw` files

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
    resources/  # plugin.xml, icons, templates
  dotnet/
    CvwSupport/ # ReSharper backend (net472)
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

# Run a sandbox Rider instance with the plugin loaded
./gradlew runIde
```

## Related

- [ConsoleMVC](https://github.com/MateuszPodeszwa/ConsoleMVC): the .NET framework these plugins support
- [ConsoleMVC.Framework on NuGet](https://www.nuget.org/packages/ConsoleMVC.Framework): the NuGet package
- [ConsoleMVC.Preview on NuGet](https://www.nuget.org/packages/ConsoleMVC.Preview): the `cvw-preview` tool

## License

MIT
