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
- Wire CvwGeneratedDocumentFactory into ReSharper's ISecondaryDocumentGenerationService
- Enable Ctrl+Click on @model to resolve to CLR type definitions
- Full semantic code completion in the code body via the generated document
- Type-aware inspections (model type mismatch with controller's View() call)
- Rename/move refactoring propagation to .cvw files
- Test end-to-end in a real Rider instance with a ConsoleMVC project
