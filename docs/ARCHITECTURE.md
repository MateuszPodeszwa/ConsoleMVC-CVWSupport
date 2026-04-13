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

Action: `ConsoleMVC.NewCvwFile` in `NewGroup`

## Phase 2 — Backend (Future)

Will add a ReSharper backend component that:
- Creates virtual C# documents from `.cvw` files (mirroring the source generator output)
- Injects these into Rider's C# analysis engine
- Provides full semantic completion, error checking, and refactoring

This mirrors how Rider handles `.cshtml` (Razor) files internally.

### Backend Implementation Strategy

1. Create a .NET project targeting `netstandard2.0` with `JetBrains.ReSharper.SDK`
2. Implement `IProjectFileType` for `.cvw` files
3. Implement `GeneratedDocumentService` to create virtual C# documents
4. The virtual document wraps the .cvw code body in the same class structure
   the source generator produces (see CLAUDE.md Section 3.2)
5. Register the backend via Rider's protocol connection

### Build System

- Gradle with `intellij-platform-gradle-plugin` 2.14.0
- Kotlin JVM 2.1.20 with JDK 21 toolchain
- Target: Rider 2025.1+ (sinceBuild = 251)
- Plugin ID: `com.github.mateuszpodeszwa.consolemvc.cvwsupport`
