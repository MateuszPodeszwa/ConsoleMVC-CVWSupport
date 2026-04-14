# Development Log

## 2026-04-14 — Session 1: Project Bootstrap & Tier 1

### Decisions Made
- **Build system**: Gradle with `intellij-platform-gradle-plugin` 2.14.0
- **Target IDE**: Rider 2025.1+ (IntelliJ Platform 2025.1, sinceBuild=251)
- **Language**: Kotlin for frontend plugin code
- **Strategy**: Frontend-first (Phase 1), then ReSharper backend (Phase 2)
- **Java**: OpenJDK 25.0.2 (installed via Homebrew — JDK 21 toolchain target)
- **Gradle**: 9.4.1 (installed via Homebrew)
- **Rider SDK**: Using default `useInstaller=true` (the `riderRD` non-installer variant requires ~10GB+ extraction and caused disk space issues on this machine)
- **Lexer**: Hand-written (not JFlex) for simplicity. Two-mode: directive + C# code body.
- **Plugin ID**: `com.github.mateuszpodeszwa.consolemvc.cvwsupport`
- **Package**: `com.github.mateuszpodeszwa.consolemvc.cvwsupport`

### Work Completed

#### Tier 1 — Essential (COMPLETE)

1. **File Type Registration**
   - `CvwLanguage` singleton extending `Language`
   - `CvwFileType` with `.cvw` extension, custom SVG icon
   - Registered in `plugin.xml` as `fileType` extension

2. **Syntax Highlighting**
   - Hand-written `CvwLexer` with directive mode and C# code mode
   - Token types: directive keywords, directive arguments, C# keywords/strings/numbers/comments/identifiers/operators/punctuation
   - `CvwSyntaxHighlighter` mapping all token types to IntelliJ text attribute keys
   - Directive keywords (@model, @using) highlighted as keywords
   - Directive arguments highlighted as class references

3. **Code Completion**
   - `CvwCompletionContributor` with two providers:
     - Directive completion: `@model` and `@using` at line starts in directive section
     - Code body completion: `NavigationResult.To/ToAction/Quit`, `Console.*`, `Model`, `ViewData`, `return`
   - Insert handlers place cursor inside parentheses/quotes

4. **Error Highlighting**
   - `CvwAnnotator` at file level with checks:
     - Missing `@model` directive (warning — file will be skipped by generator)
     - Empty `@model`/`@using` arguments (error)
     - Empty code body (warning)
     - Missing `return` statement (warning)
     - Incomplete directives like bare `@model` without space (error)

5. **Editor Features**
   - `CvwBraceMatcher` for paired braces
   - `CvwCommenter` for `//` and `/* */` comment toggling

#### Tier 2 — Important (COMPLETE — frontend scope)

5. **Navigation**
   - `CvwViewLineMarkerProvider`: gutter icon on .cvw files linking to controller
   - `CvwGotoRelatedProvider`: bidirectional view <-> controller navigation
   - Uses naming convention: `Views/{Name}/` -> `{Name}Controller.cs`

6. **Live Templates**
   - `cvw` — full .cvw file scaffold with @model and return
   - `navto` — `NavigationResult.To("Controller", "Action")`
   - `navaction` — `NavigationResult.ToAction("Action")`
   - `navquit` — `NavigationResult.Quit()`
   - Registered with `CvwLiveTemplateContext` for .cvw files

7. **File Templates**
   - "ConsoleMVC View" internal file template
   - `CvwCreateFileAction` added to New menu group

#### Tier 3 — Nice to Have (PARTIAL)

8. **Structure View**
   - Shows @model type, @using imports, and NavigationResult targets
   - Targets extracted via regex from the code body

9. **Editor Tab Title**
   - `CvwEditorTabTitleProvider` shows "Controller/ActionView" format

10. **Improved Brace Matching**
    - Split `CS_PUNCTUATION` into individual `CS_LBRACE`/`CS_RBRACE`, `CS_LPAREN`/`CS_RPAREN`, `CS_LBRACKET`/`CS_RBRACKET`
    - Each pair has proper matching and distinct highlight colors

### Known Issues / Limitations
- C# highlighting in the code body is token-level only (no semantic analysis). Types are not resolved. Full semantic highlighting requires the Phase 2 ReSharper backend.
- Code completion for `@model` type names and `@using` namespaces is not yet context-aware (no project type resolution in Phase 1).
- The `useInstaller=true` Rider SDK warning in Gradle is cosmetic and does not affect the build.
- Refactoring support (rename/move model updates .cvw files) requires Phase 2 backend.
- Ctrl+Click on @model type to navigate to the type definition requires Phase 2 backend.

### Additional Frontend Features (continued in same session)

11. **Code Folding**
    - `CvwFoldingBuilder`: folds directive preamble, `{ }` brace blocks, and `/* */` comments
    - Includes `isInsideStringOrComment` heuristic to avoid folding braces inside strings

12. **Color Settings Page**
    - `CvwColorSettingsPage` in Settings > Editor > Color Scheme > CVW
    - Allows customization of all 15 token highlight colors
    - Includes demo text showing all token types

#### Phase 2 — Backend (STARTED)

13. **ReSharper Backend Structure**
    - Created `src/dotnet/CvwSupport.sln` with `CvwSupport.csproj` targeting `net472`
    - NuGet: `JetBrains.Rider.SDK` 2025.1.1 + `Microsoft.NETFramework.ReferenceAssemblies` for cross-platform build
    - `ZoneMarker.cs`: `ICvwSupportZone` extending `IPsiLanguageZone`
    - `CvwProjectFileType.cs`: `.cvw` file type registration on the backend
    - `CvwLanguage.cs`: PSI language definition
    - `CvwProjectFileLanguageService.cs`: bridges file type to language
    - Gradle integration: `buildBackend` task + `PrepareSandboxTask` copies DLLs to `dotnet/` folder

14. **Virtual Document Generation**
    - `CvwFileParser.cs`: parses .cvw directives and code body with precise char offsets
    - `CvwGeneratedDocumentFactory.cs`: generates synthetic C# documents mirroring the source generator output
    - Bidirectional offset mapping between original .cvw and generated C# positions
    - `CvwPsiFileManager.cs`: solution component exposing document generation to ReSharper

### Build Verification
- `./gradlew build` passes with BUILD SUCCESSFUL (frontend Kotlin)
- `./gradlew buildBackend` passes with BUILD SUCCESSFUL (backend C#)
- `dotnet build CvwSupport.sln` succeeds with 0 errors
- Plugin JAR produced at `build/libs/ConsoleMVC-CvwSupport-0.1.0.jar`

### Next Steps (Phase 2 — Remaining Backend Work)
- ~~Wire CvwGeneratedDocumentFactory into ReSharper's ISecondaryDocumentGenerationService~~ DONE
- Enable Ctrl+Click on @model to resolve to CLR type definitions
- Full semantic code completion in the code body via the generated document
- Type-aware inspections (model type mismatch with controller's View() call)
- Rename/move refactoring propagation to .cvw files
- Test end-to-end in a real Rider instance with a ConsoleMVC project

## 2026-04-14 — Session 2: Backend Generated Document Service Wiring

### Decisions Made
- **Base class**: `GeneratedDocumentServiceBase` from `JetBrains.ReSharper.Psi.Web.Generation` namespace (`JetBrains.ReSharper.Psi.Web.dll`). This is the same base class Razor uses.
- **Registration**: `[GeneratedDocumentService(typeof(CvwProjectFileType), Instantiation.DemandAnyThreadSafe)]` attribute from `JetBrains.ReSharper.Psi.ExtensionsAPI`.
- **Range mapping**: Uses typed `TreeTextRange<Generated>` / `TreeTextRange<Original>` (not plain `TreeTextRange`) with `IGeneratedRangeMap.Add()`.
- **Root namespace**: Falls back to project name since `IProject` doesn't expose `GetDefaultNamespace` directly. The ConsoleMVC source generator uses MSBuild's `RootNamespace` property which typically matches the project name.
- **DLL location**: `JetBrains.ReSharper.Psi.Web.dll` is transitively available through `JetBrains.Rider.SDK` via the `jetbrains.psi.features.web.core` package.

### Work Completed

15. **CvwGeneratedDocumentService**
    - Created `CvwGeneratedDocumentService.cs` extending `GeneratedDocumentServiceBase`
    - Implements `Generate()`: uses `CvwGeneratedDocumentFactory` to produce virtual C#, builds range map with `GeneratedRangeMapFactory.CreateGeneratedRangeMap()`, returns `SecondaryDocumentGenerationResult` with C# language type and C# lexer factory
    - Implements `GetSecondaryPsiLanguageTypes()`: yields `CSharpLanguage.Instance`
    - Implements `IsSecondaryPsiLanguageType()`: checks `language.Is<CSharpLanguage>()`
    - Range map: maps each code body line with typed `TreeTextRange<Generated>` / `TreeTextRange<Original>` offsets, accounting for the 12-char indentation prefix added to each line
    - Empty result fallback for files without `@model` directive

### Build Verification
- `dotnet build CvwSupport.sln` succeeds with 0 errors
- `./gradlew buildBackend` passes with BUILD SUCCESSFUL

### API Discovery Notes (for future sessions)
- `GeneratedDocumentServiceBase` constructor: parameterless
- Abstract methods to override: `Generate()`, `GetSecondaryPsiLanguageTypes()`, `IsSecondaryPsiLanguageType()`
- `SecondaryDocumentGenerationResult` ctor: `(string text, PsiLanguageType language, ISecondaryRangeTranslator, ILexerFactory)`
- `RangeTranslatorWithGeneratedRangeMap` ctor: `(IGeneratedRangeMap)`
- `GeneratedRangeMapFactory.CreateGeneratedRangeMap(IFile originalFile)` — static factory
- `IGeneratedRangeMap.Add(TreeTextRange<Generated>, TreeTextRange<Original>)` — for building the range map
- `GeneratedDocumentServiceAttribute` ctor: `(Type type, Instantiation instantiation)` — registration attribute
- `CSharpLanguage` is in `JetBrains.ReSharper.Psi.CSharp` namespace / `JetBrains.ReSharper.Psi.CSharp.dll`

16. **Directive Argument Range Mapping**
    - Extended `CvwFileParser` to track `DirectiveArgLocation` (offset + length) for @model and @using arguments
    - Extended `CvwGeneratedDocumentFactory` to track where model type appears in generated text (ConsoleView<T> + Render parameter)
    - Extended `CvwGeneratedDocumentFactory` to track where @using namespaces appear in generated text
    - `BuildRangeMap` now maps directive arguments to their generated C# counterparts
    - This enables Ctrl+Click on @model type → CLR type definition and @using namespace → namespace definition

17. **Frontend: QuickDoc Provider**
    - `CvwDocumentationProvider` shows hover documentation for:
      - `@model` and `@using` directive keywords
      - Directive arguments (model type, namespace)
      - Key identifiers: `Model`, `ViewData`, `NavigationResult`
      - `NavigationResult.To()`, `.ToAction()`, `.Quit()` static methods

18. **Frontend: Extend Selection Handler**
    - `CvwWordSelectionHandler` improves Ctrl+W behavior:
      - Directive argument → full directive → all directives
      - Code body selection as a unit

19. **Lexer Edge Case Fixes**
    - Fixed verbatim string scanner (`@"..."`) offset handling
    - Added C# verbatim identifier support (`@class`, `@event`)
    - Added preprocessor directive support (`#region`, `#if`) as line comments
    - Fixed `$@"..."` interpolated verbatim string offset

20. **Quick-Fixes**
    - `AddModelDirectiveQuickFix`: inserts `@model \n\n` at file start, positions cursor after `@model `
    - `AddReturnStatementQuickFix`: appends `return NavigationResult.Quit();\n` at end of code body
    - Both wired into the annotator's warning annotations

21. **NavigationResult.To() Gutter Navigation**
    - Extended `CvwViewLineMarkerProvider` to detect `NavigationResult.To("Controller", "Action")` patterns
    - Shows gutter icon on the controller name string literal linking to the controller file
    - Uses token-level pattern matching: walks backwards from string to verify `NavigationResult.To(` pattern

22. **Additional Test Data**
    - `EdgeCases.cvw`: verbatim strings, interpolated verbatim, verbatim identifiers, preprocessor directives
    - `NoModel.cvw`: missing @model directive (tests annotator warning)
    - `ComplexCodeBody.cvw`: LINQ, nullable references, ternary, null-coalescing

### Build Verification
- `dotnet build CvwSupport.sln` succeeds with 0 errors
- `./gradlew buildPlugin` passes with BUILD SUCCESSFUL
- Plugin distribution: `build/distributions/ConsoleMVC-CvwSupport-0.1.0.zip` (102KB)
  - Contains `lib/ConsoleMVC-CvwSupport-0.1.0.jar` (frontend) and `dotnet/CvwSupport.dll` (backend)

### Next Steps
- Cross-file inspections (model type mismatch with controller's View() call) — requires daemon stage
- End-to-end testing in Rider with a real ConsoleMVC project
