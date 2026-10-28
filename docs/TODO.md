# TODO: ConsoleMVC .cvw Plugin

## Tier 1: Essential (Must Have) -- COMPLETE

- [x] File type registration (`.cvw` extension, icon, language)
- [x] Syntax highlighting (directives + C# code body)
- [x] Code completion (directives + code body basics)
- [x] Error highlighting (missing `@model`, empty args, missing return)
- [x] Brace matching (individual { } ( ) [ ] pairs) and commenter

## Tier 2: Important (Should Have) -- COMPLETE (frontend scope)

- [x] Navigation: gutter icons on .cvw files linking to controllers
- [x] Navigation: Go to Related between .cvw views and controllers
- [x] Live templates: `cvw`, `navto`, `navaction`, `navquit`
- [x] File templates: "New ConsoleMVC View" with @model scaffold
- [x] Refactoring support (rename/move model updates `.cvw` files): enabled by GeneratedDocumentService range mapping

## Tier 3: Nice to Have -- COMPLETE

- [x] Structure view (@model, @using, NavigationResult targets)
- [x] Editor tab title: "Controller/ActionView" format
- [x] Brace matching: individual { } ( ) [ ] token types
- [x] Code folding (directives, brace blocks, multi-line comments)
- [x] Color settings page (all 15 token types customizable)
- [x] Quick documentation (hover docs for directives, Model, ViewData, NavigationResult)
- [x] Extend selection handler (smart Ctrl+W for directives and code body)
- [x] Lexer: verbatim identifiers (@class), preprocessor directives (#region)
- [x] Quick-fixes: "Add @model directive" and "Add return NavigationResult.Quit()"
- [x] NavigationResult.To() gutter icons linking to target controller files
- [x] Inspections (model type mismatch with controller's View() call): frontend heuristic via CvwModelMismatchAnnotator

## Backend (Phase 2): IN PROGRESS

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
- [x] Refactoring (rename/move model class updates .cvw files): via range mapping
- [x] Cross-file inspections (model type mismatch with controller's View() call): frontend heuristic annotator
- [ ] End-to-end testing in Rider with a real ConsoleMVC project

## C-View Markup in Rider (0.2.0): COMPLETE

- [x] Markup schema bundled (src/main/resources/schema/cvw-schema.json) with a Kotlin model, parser, completion, checks and documentation
- [x] Lexer: markup tokens painted over a parse of the view, C# tokens for the rest; colour settings for every token
- [x] Completion contributor and automatic pop-up after `<`, `</`, `@`, quotes and spaces in tags
- [x] External annotator: cvw-preview's diagnostics, or schema checks; the old @model and return warnings replaced
- [x] ConsoleMVC Preview tool window (JCEF, no scripts), action and Settings | Tools | ConsoleMVC
- [x] Backend: design-time document with markup left out, expressions as statements, @inject properties, the generator's namespace, base class and model
- [x] Tests: JUnit (Kotlin), the platform's lexer checks and incremental highlighter, comparison with the generator, dotnet tests of the backend against the real generator, Plugin Verifier
- [ ] Completion, checks and the preview window tried by hand in a running Rider (Rider's editor tests need a .NET solution and backend, which the test fixtures here cannot start)

## VS Code extension (vscode/) -- COMPLETE (0.1.0)

- [x] Language, file icon and grammar generated from ConsoleMVC's markup schema
- [x] Completion of elements (by placement), attributes, values, closing tags, directives and code constructs
- [x] Hover documentation for elements, attributes and directives
- [x] Checks while typing: the generator's diagnostics through cvw-preview, or schema checks without it
- [x] Live preview panel through cvw-preview, with a strict Content Security Policy
- [x] Snippets, settings, untrusted-folder support
- [x] Unit tests (node:test), grammar tests (vscode-textmate), integration tests in VS Code, CI and packaging
- [ ] Publish to the Visual Studio Marketplace (needs the VSCE_PAT secret)
