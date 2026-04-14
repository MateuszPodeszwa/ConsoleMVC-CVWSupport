# TODO — ConsoleMVC .cvw Plugin

## Tier 1 — Essential (Must Have) -- COMPLETE

- [x] File type registration (`.cvw` extension, icon, language)
- [x] Syntax highlighting (directives + C# code body)
- [x] Code completion (directives + code body basics)
- [x] Error highlighting (missing `@model`, empty args, missing return)
- [x] Brace matching (individual { } ( ) [ ] pairs) and commenter

## Tier 2 — Important (Should Have) -- COMPLETE (frontend scope)

- [x] Navigation: gutter icons on .cvw files linking to controllers
- [x] Navigation: Go to Related between .cvw views and controllers
- [x] Live templates: `cvw`, `navto`, `navaction`, `navquit`
- [x] File templates: "New ConsoleMVC View" with @model scaffold
- [x] Refactoring support (rename/move model updates `.cvw` files) — enabled by GeneratedDocumentService range mapping

## Tier 3 — Nice to Have -- COMPLETE

- [x] Structure view (@model, @using, NavigationResult targets)
- [x] Editor tab title: "Controller/ActionView" format
- [x] Brace matching: individual { } ( ) [ ] token types
- [x] Code folding (directives, brace blocks, multi-line comments)
- [x] Color settings page (all 15 token types customizable)
- [x] Quick documentation (hover docs for directives, Model, ViewData, NavigationResult)
- [x] Extend selection handler (smart Ctrl+W for directives and code body)
- [x] Lexer: verbatim identifiers (@class), preprocessor directives (#region)
- [ ] Inspections/quick-fixes (model type mismatch, missing view) — requires cross-file backend analysis

## Backend (Phase 2) — IN PROGRESS

- [x] ReSharper backend project structure (net472, JetBrains.Rider.SDK 2025.1.1)
- [x] File type registration on backend (CvwProjectFileType)
- [x] PSI language definition (CvwLanguage)
- [x] Project file language service (CvwProjectFileLanguageService)
- [x] Zone marker for component model
- [x] Gradle build integration (buildBackend task + PrepareSandboxTask)
- [x] Virtual document generator (CvwFileParser + CvwGeneratedDocumentFactory)
- [x] Bidirectional offset mapping (original <-> generated)
- [x] PSI file manager solution component
- [x] Wire into GeneratedDocumentServiceBase for live C# analysis
- [x] Ctrl+Click on @model type to navigate to CLR type definition (via range mapping)
- [x] Full semantic C# completion in code body via generated document (enabled by GeneratedDocumentService)
- [x] Semantic error checking (type mismatches, unresolved references) (enabled by GeneratedDocumentService)
- [x] Refactoring (rename/move model class updates .cvw files) — via range mapping
- [ ] Inspections (model type mismatch with controller's View() call)
- [ ] End-to-end testing in Rider with a real ConsoleMVC project
