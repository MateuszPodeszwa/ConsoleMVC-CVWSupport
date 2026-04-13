# Architecture — ConsoleMVC .cvw Plugin

## Overview

This is a JetBrains Rider plugin providing IDE support for `.cvw` (Console View) files.
It follows the standard Rider plugin dual architecture:

- **Frontend (Kotlin/IntelliJ)**: UI, file type, editor, syntax highlighting, basic completion
- **Backend (C#/ReSharper)**: Deep C# analysis, type resolution, refactoring (Phase 2)

## Phase 1 — Frontend Only (Current)

### Module Structure

```
src/main/kotlin/com/consolemvc/rider/
  CvwLanguage.kt          — Language singleton
  CvwFileType.kt          — FileType with .cvw extension and icon
  CvwIcons.kt             — Icon constants

  lexer/
    CvwLexer.kt           — Custom lexer (directive mode + C# delegation)
    CvwTokenTypes.kt      — Token type definitions
    _CvwLexer.flex         — JFlex lexer spec (if using JFlex)

  parser/
    CvwParserDefinition.kt — Parser definition
    CvwParser.kt           — Parser producing PSI tree
    CvwElementTypes.kt     — PSI element types

  highlighting/
    CvwSyntaxHighlighter.kt          — Maps tokens to text attributes
    CvwSyntaxHighlighterFactory.kt   — Factory registration
    CvwColorSettingsPage.kt          — Color settings (optional)

  completion/
    CvwCompletionContributor.kt      — Completion for directives

  annotator/
    CvwAnnotator.kt                  — Error annotations (missing @model, etc.)
```

### Key Design Decisions

1. **Lexer approach**: Custom lexer with two modes:
   - Directive mode: tokenizes `@model`/`@using` keywords and their arguments
   - Code mode: treats remaining content as C# code tokens

2. **C# highlighting in code body**: For Phase 1, we use TextMate-style or
   token-based highlighting. Full semantic highlighting comes in Phase 2 with
   the ReSharper backend.

3. **PSI tree**: Simple tree with `CvwFile` root, directive nodes, and a
   code body node. The code body is opaque in Phase 1.

## Phase 2 — Backend (Future)

Will add a ReSharper backend component that:
- Creates virtual C# documents from `.cvw` files (mirroring the source generator output)
- Injects these into Rider's C# analysis engine
- Provides full semantic completion, error checking, and refactoring

This mirrors how Rider handles `.cshtml` (Razor) files internally.

## Resources

- Plugin icon: `src/main/resources/icons/cvw.svg`
- Plugin descriptor: `src/main/resources/META-INF/plugin.xml`
