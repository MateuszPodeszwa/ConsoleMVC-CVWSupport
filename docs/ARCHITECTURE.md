# Architecture — ConsoleMVC .cvw Plugin

## Overview

This is a JetBrains Rider plugin providing IDE support for `.cvw` (Console View) files.
It follows the standard Rider plugin dual architecture:

- **Frontend (Kotlin/IntelliJ)**: UI, file type, editor, syntax highlighting, completion, navigation
- **Backend (C#/ReSharper)**: Deep C# analysis, type resolution, refactoring (Phase 2 — not yet started)

## Current State: Phase 1 — Frontend Only

### Key Files

```
src/main/kotlin/com/github/mateuszpodeszwa/consolemvc/cvwsupport/
  CvwLanguage.kt                — Language singleton ("CVW")
  CvwFileType.kt                — FileType with .cvw extension and icon
  CvwIcons.kt                   — Icon loader (/icons/cvw.svg)
  CvwBraceMatcher.kt            — Paired brace matching ({}, (), [])
  CvwCommenter.kt               — // and /* */ comment toggling
  CvwEditorTabTitleProvider.kt   — Shows "Controller/ActionView" in tabs
  CvwLiveTemplateContext.kt      — Template context for .cvw files
  CvwCreateFileAction.kt         — "New ConsoleMVC View" action in New menu

  lexer/
    CvwTokenTypes.kt             — All token type definitions
    CvwLexer.kt                  — Hand-written two-mode lexer

  parser/
    CvwParser.kt                 — Splits file into directives + code body
    CvwParserDefinition.kt       — Registers lexer, parser, file creation

  psi/
    CvwElementTypes.kt           — PSI element types (FILE, MODEL_DIRECTIVE, etc.)
    CvwFile.kt                   — PsiFile implementation

  highlighting/
    CvwSyntaxHighlighter.kt      — Token-to-color mapping
    CvwSyntaxHighlighterFactory.kt — Factory registration

  completion/
    CvwCompletionContributor.kt  — Directive + code body completion

  annotator/
    CvwAnnotator.kt              — Error/warning annotations

  navigation/
    CvwViewLineMarkerProvider.kt — Gutter icons linking .cvw to controllers
    CvwGotoRelatedProvider.kt    — Bidirectional view <-> controller nav

  structure/
    CvwStructureViewFactory.kt   — Structure view factory
    CvwStructureViewModel.kt     — Structure view model
    CvwStructureViewElement.kt   — Tree elements (directives, nav targets)

src/main/resources/
  META-INF/plugin.xml            — Plugin descriptor with all extension registrations
  icons/cvw.svg                  — File icon (purple rounded rect with "cvw" text)
  liveTemplates/CvwTemplates.xml — Live template definitions
  fileTemplates/internal/
    ConsoleMVC View.cvw.ft       — New file template
```

### Lexer Design

The lexer (`CvwLexer`) is hand-written (not JFlex) with two modes:

1. **Directive mode** (initial state = 0):
   - Recognizes `@model` and `@using` as keywords
   - Reads their arguments as `DIRECTIVE_ARGUMENT` tokens
   - Blank lines keep us in directive mode
   - First non-directive, non-blank content switches to code mode

2. **Code mode** (state = 1):
   - Tokenizes C# code body with basic categories:
   - Keywords (C# reserved words), strings (regular, verbatim, interpolated),
     numbers, comments (line/block), identifiers, operators, individual brace types,
     other punctuation (semicolons, commas), and dots

### PSI Tree Structure

```
CvwFile (root)
  ├─ MODEL_DIRECTIVE
  │    ├─ MODEL_KEYWORD ("@model")
  │    ├─ WHITE_SPACE
  │    └─ DIRECTIVE_ARGUMENT ("Fully.Qualified.Type")
  ├─ USING_DIRECTIVE (0..N)
  │    ├─ USING_KEYWORD ("@using")
  │    ├─ WHITE_SPACE
  │    └─ DIRECTIVE_ARGUMENT ("Namespace.Name")
  └─ CODE_BODY
       └─ (all remaining C# tokens)
```

### Plugin Registration (plugin.xml)

All extensions are registered under `com.intellij.` namespace:
- `fileType` — CvwFileType
- `lang.parserDefinition` — CvwParserDefinition
- `lang.syntaxHighlighterFactory` — CvwSyntaxHighlighterFactory
- `completion.contributor` — CvwCompletionContributor
- `annotator` — CvwAnnotator
- `lang.braceMatcher` — CvwBraceMatcher
- `lang.commenter` — CvwCommenter
- `liveTemplateContext` — CvwLiveTemplateContext
- `defaultLiveTemplates` — /liveTemplates/CvwTemplates
- `internalFileTemplate` — ConsoleMVC View.cvw
- `codeInsight.lineMarkerProvider` — CvwViewLineMarkerProvider
- `gotoRelatedProvider` — CvwGotoRelatedProvider
- `lang.psiStructureViewFactory` — CvwStructureViewFactory
- `editorTabTitleProvider` — CvwEditorTabTitleProvider
- `lang.foldingBuilder` — CvwFoldingBuilder
- `colorSettingsPage` — CvwColorSettingsPage

Action: `ConsoleMVC.NewCvwFile` in `NewGroup`

## Phase 2 — Backend (In Progress)

### Backend File Structure

```
src/dotnet/
  CvwSupport.sln                           — .NET solution
  Plugin.props                             — SDK version (2025.1.1)
  Directory.Build.props                    — Common build properties
  CvwSupport/
    CvwSupport.csproj                      — net472 + JetBrains.Rider.SDK
    ZoneMarker.cs                          — ICvwSupportZone + ZoneMarker
    CvwProjectFileType.cs                  — .cvw file type registration
    CvwLanguage.cs                         — PSI language definition
    CvwProjectFileLanguageService.cs       — File type -> language bridge
    CvwFileParser.cs                       — Parses .cvw into directives + code body
    CvwGeneratedDocumentFactory.cs         — Generates virtual C# documents
    CvwPsiFileManager.cs                   — Solution component for document access
    CvwGeneratedDocumentService.cs         — Wires into ReSharper's secondary document pipeline
```

### Virtual Document Generation

The key insight: ReSharper analyzes C# code. A `.cvw` file is NOT C#, but its
code body IS C# that will be placed inside a method body. So we create an
in-memory C# document that wraps the code body exactly as the source generator would:

```csharp
// Original .cvw:
@model MyApp.Models.HomeViewModel
@using System.Globalization
Console.WriteLine(Model.Title);
return NavigationResult.Quit();

// Generated virtual C# document:
using ConsoleMVC.Mvc;
using System.Globalization;
namespace MyApp.Views.Home {
  public class IndexView : ConsoleView<MyApp.Models.HomeViewModel> {
    public override NavigationResult Render(MyApp.Models.HomeViewModel Model) {
      Console.WriteLine(Model.Title);
      return NavigationResult.Quit();
    }
  }
}
```

A **range map** tracks character offsets so errors/completions in the generated
document map back to correct positions in the original `.cvw` file.

### Secondary Document Service Wiring

`CvwGeneratedDocumentService` extends `GeneratedDocumentServiceBase` (from `JetBrains.ReSharper.Psi.Web.Generation`)
and is registered via `[GeneratedDocumentService(typeof(CvwProjectFileType))]`. This is the same mechanism
Razor uses for `.cshtml` files. When ReSharper opens a `.cvw` file:

1. The service's `Generate()` method is called with the primary file info
2. It uses `CvwGeneratedDocumentFactory` to create the virtual C# document
3. A `RangeTranslatorWithGeneratedRangeMap` maps typed `TreeTextRange<Generated>` / `TreeTextRange<Original>` offsets
4. The result is returned as a `SecondaryDocumentGenerationResult` with C# language type
5. ReSharper's C# engine analyzes the generated document
6. Errors, completions, and navigation are projected back via the range map

### What's Left for Backend

The secondary document generation pipeline is wired. What remains:
1. Verify end-to-end in a running Rider instance with a real ConsoleMVC project
2. Ctrl+Click on `@model` type to navigate to CLR type definition
3. Full semantic C# completion in code body via generated document
4. Semantic error checking (type mismatches, unresolved references)
5. Refactoring (rename/move model class updates .cvw files)
6. Inspections (model type mismatch with controller's View() call)

### Build System

- **Frontend**: Gradle with `intellij-platform-gradle-plugin` 2.14.0, Kotlin JVM 2.1.20, JDK 21
- **Backend**: `dotnet build` via Gradle `buildBackend` task, net472, JetBrains.Rider.SDK 2025.1.1
- **Integration**: `PrepareSandboxTask` copies backend DLLs to `{plugin}/dotnet/` in the sandbox
- Target: Rider 2025.1+ (sinceBuild = 251)
- Plugin ID: `com.github.mateuszpodeszwa.consolemvc.cvwsupport`
