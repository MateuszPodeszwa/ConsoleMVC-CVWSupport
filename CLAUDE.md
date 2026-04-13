# Rider Plugin Request: ConsoleMVC `.cvw` File Support

## Objective

Build a JetBrains Rider (IntelliJ Platform) plugin that provides first-class IDE support for `.cvw` (Console View) files used by the [ConsoleMVC](https://github.com/MateuszPodeszwa/ConsoleMVC) .NET framework. The `.cvw` format is structurally similar to ASP.NET Core Razor (`.cshtml`) but simpler — it is plain C# with a small directive preamble. The plugin should make editing `.cvw` files feel as natural as editing `.cs` files inside Rider.

---

## Table of Contents

1. [What is ConsoleMVC?](#1-what-is-consolemvc)
2. [The `.cvw` File Format — Complete Specification](#2-the-cvw-file-format--complete-specification)
3. [How `.cvw` Files Are Compiled — The Source Generator](#3-how-cvw-files-are-compiled--the-source-generator)
4. [Framework Types Available Inside `.cvw` Files](#4-framework-types-available-inside-cvw-files)
5. [Project Conventions and File Layout](#5-project-conventions-and-file-layout)
6. [Build System Integration](#6-build-system-integration)
7. [Complete Example: End-to-End Flow](#7-complete-example-end-to-end-flow)
8. [Plugin Feature Requirements](#8-plugin-feature-requirements)
9. [Plugin Architecture Guidance](#9-plugin-architecture-guidance)
10. [Reference: Full Source Generator Code](#10-reference-full-source-generator-code)
11. [Reference: All Framework Types (Complete Source)](#11-reference-all-framework-types-complete-source)
12. [Reference: Example `.cvw` Files](#12-reference-example-cvw-files)

---

## 1. What is ConsoleMVC?

ConsoleMVC is a .NET 10.0 framework that brings ASP.NET Core MVC patterns to console applications. It ships as a NuGet package (`ConsoleMVC.Framework`) containing:

- A runtime library with base classes (`Controller`, `ConsoleView<TModel>`, `ActionResult`, `NavigationResult`, etc.)
- A bundled Roslyn incremental source generator that compiles `.cvw` view files into C# classes at build time

The framework follows an event loop: resolve controller -> invoke action -> process result -> render view -> repeat until exit.

**NuGet package:** `ConsoleMVC.Framework` (latest: 1.0.2)
**Target framework:** .NET 10.0
**Repository:** https://github.com/MateuszPodeszwa/ConsoleMVC

---

## 2. The `.cvw` File Format — Complete Specification

A `.cvw` (Console View) file is a plain-text file that compiles into a `ConsoleView<TModel>` subclass. It has two sections: a **directive preamble** and a **C# code body**.

### 2.1 Directive Preamble

Directives appear at the top of the file, one per line, prefixed with `@`. They must come before any code. Blank lines between directives are allowed and ignored.

| Directive | Syntax | Required | Purpose |
|-----------|--------|----------|---------|
| `@model` | `@model Fully.Qualified.TypeName` | **Yes** | Declares the view model type. Becomes the generic argument of `ConsoleView<T>` and the type of the `Model` parameter in `Render()`. |
| `@using` | `@using Some.Namespace` | No | Adds a `using` statement to the generated class. Multiple `@using` directives are allowed. |

**Rules:**
- `@model` is mandatory. Files without it are silently skipped by the generator.
- `@using` is optional and repeatable.
- Directives are parsed by checking `trimmedLine.StartsWith("@model ")` and `trimmedLine.StartsWith("@using ")` — the space after the keyword is significant.
- The first non-blank, non-directive line marks the start of the code body. Once the code body starts, no more directives are recognized.

### 2.2 C# Code Body

Everything after the directive preamble is **raw C# code**. It becomes the body of the generated `Render(TModel Model)` method. This means:

- The code executes inside a method body — local variables, control flow, expressions, etc. are all valid.
- The code has access to a parameter named `Model` of the type declared by `@model`.
- The code has access to `this.ViewData` (a `Dictionary<string, object?>`).
- The code **must return** a `NavigationResult` — the method signature is `public override NavigationResult Render(TModel Model)`.
- Standard C# `using` directives from the generated file apply: `ConsoleMVC.Mvc` is always imported, plus any `@using` directives from the `.cvw` file.
- There is no templating/interpolation syntax (no `@Variable` like Razor). All output is done via normal C# calls like `Console.WriteLine()`.

### 2.3 What `.cvw` Is NOT

Unlike Razor (`.cshtml`):
- There are **no HTML transitions** — no `<tags>` with special meaning.
- There is **no `@` expression syntax** for inline output — `@Model.Name` is NOT valid; use `Console.WriteLine(Model.Name)` instead.
- There are **no sections, layouts, or partial views**.
- There is **no `@{ ... }` code block syntax** — the entire body IS a code block.

The only `@`-prefixed tokens are the directive lines (`@model`, `@using`) at the top.

### 2.4 Formal Grammar (Pseudo-BNF)

```
cvw-file       = directive-section code-body
directive-section = *(blank-line / model-directive / using-directive)
model-directive   = "@model " fully-qualified-type-name NEWLINE
using-directive   = "@using " namespace-name NEWLINE
blank-line        = *WHITESPACE NEWLINE
code-body         = *csharp-statement   ; raw C# forming the Render() method body
                                         ; must return NavigationResult
```

---

## 3. How `.cvw` Files Are Compiled — The Source Generator

The source generator (`ViewSourceGenerator`) is a Roslyn **incremental source generator** targeting `netstandard2.0`. It runs at compile time inside the C# compiler process.

### 3.1 Input

- **AdditionalFiles** matching `Views/**/*.cvw` — globbed automatically by `ConsoleMVC.Framework.props` (auto-imported into consuming projects via NuGet).
- **Build property** `RootNamespace` — exposed to the generator via `CompilerVisibleProperty` in the same `.props` file.

### 3.2 Transformation

For a file at `Views/{ControllerName}/{ActionName}View.cvw`, the generator produces a class:

```csharp
// <auto-generated>
// Generated by ConsoleMVC ViewEngine from {ControllerName}/{ActionName}View.cvw
// Do not modify this file directly — edit the .cvw source instead.
// </auto-generated>
using ConsoleMVC.Mvc;
using {each @using namespace};

namespace {RootNamespace}.Views.{ControllerName}
{
    public class {ActionName}View : ConsoleView<{@model type}>
    {
        public override NavigationResult Render({@model type} Model)
        {
            {code body from .cvw, indented 8 spaces}
        }
    }
}
```

### 3.3 Key Behaviors

- The generator extracts `ControllerName` by finding the `Views` segment in the file path and taking the next segment.
- The class name is the file name without extension (e.g., `IndexView.cvw` -> class `IndexView`).
- The generated file is named `{ControllerName}_{FileName}.g.cs` (e.g., `Home_IndexView.g.cs`).
- If `@model` is missing, the file is silently skipped (no error, no output).
- If the `Views` directory segment cannot be found in the path, the file is silently skipped.
- `using ConsoleMVC.Mvc;` is always emitted as the first using directive.
- The `RootNamespace` defaults to `"ConsoleMVC"` if the build property is not set.

### 3.4 What This Means for the Plugin

The plugin does NOT need to run the source generator. Instead, it needs to **understand the transformation** so it can provide IDE features by knowing:

1. What type `Model` is (from the `@model` directive) — needed for code completion.
2. What namespaces are imported (from `@using` directives + the implicit `ConsoleMVC.Mvc`) — needed for type resolution.
3. That the code body is a method body returning `NavigationResult` — needed for error highlighting and completions.
4. Where the file sits in the project (`Views/{Controller}/{Action}View.cvw`) — needed for navigation features.

---

## 4. Framework Types Available Inside `.cvw` Files

These types are from the `ConsoleMVC.Mvc` namespace (always imported in generated code). The plugin needs awareness of these for completions and validation.

### 4.1 `NavigationResult` — The Required Return Type

Every `.cvw` code body must return a `NavigationResult`. These are the static factory methods available:

```csharp
public class NavigationResult
{
    public string? Controller { get; init; }
    public string? Action { get; init; }
    public bool Exit { get; init; }

    // Navigate to a specific controller and action
    public static NavigationResult To(string controller, string action);

    // Navigate to a different action on the current controller
    public static NavigationResult ToAction(string action);

    // Signal the application to exit
    public static NavigationResult Quit();
}
```

### 4.2 `ConsoleView<TModel>` — The Generated Base Class

```csharp
public abstract class ConsoleView<TModel> : ConsoleView
{
    public abstract NavigationResult Render(TModel Model);
}

public abstract class ConsoleView
{
    public ViewDataDictionary ViewData { get; internal set; }
    internal abstract NavigationResult RenderInternal(object? model);
}
```

### 4.3 `ViewDataDictionary`

```csharp
public class ViewDataDictionary : Dictionary<string, object?> { }
```

Accessible as `ViewData` inside `.cvw` files (inherited from `ConsoleView` base class).

### 4.4 `Controller` — For Cross-Reference Navigation

```csharp
public abstract class Controller
{
    public RouteContext RouteContext { get; internal set; }
    public ViewDataDictionary ViewData { get; }

    protected ViewResult View(object? model = null);
    protected RedirectToActionResult RedirectToAction(string action, string? controller = null);
}
```

### 4.5 `ActionResult` Hierarchy

```csharp
public abstract class ActionResult { }

public class ViewResult : ActionResult
{
    public string ControllerName { get; init; }
    public string ViewName { get; init; }
    public object? Model { get; init; }
    public ViewDataDictionary ViewData { get; init; }
}

public class RedirectToActionResult : ActionResult
{
    public string ControllerName { get; init; }
    public string ActionName { get; init; }
}
```

### 4.6 `RouteContext`

```csharp
public class RouteContext
{
    public string Controller { get; init; } = "Home";
    public string Action { get; init; } = "Index";
}
```

---

## 5. Project Conventions and File Layout

### 5.1 Directory Structure

A ConsoleMVC project follows this layout:

```
MyApp/
  MyApp.csproj
  Program.cs
  Controllers/
    HomeController.cs         # HomeController : Controller
    SettingsController.cs     # SettingsController : Controller
  Models/
    HomeViewModel.cs          # Plain DTO
    SettingsViewModel.cs
  Views/
    Home/
      IndexView.cvw           # Generates IndexView : ConsoleView<HomeViewModel>
      AboutView.cvw           # Generates AboutView : ConsoleView<HomeViewModel>
    Settings/
      IndexView.cvw           # Generates IndexView : ConsoleView<SettingsViewModel>
```

### 5.2 Naming Conventions

| Artifact | Convention | Example |
|----------|-----------|---------|
| Controller class | `{Name}Controller : Controller` | `HomeController` |
| Action method | Public, parameterless, returns `ActionResult` | `public ActionResult Index()` |
| View file | `Views/{Controller}/{Action}View.cvw` | `Views/Home/IndexView.cvw` |
| Generated view class | `{Action}View` in namespace `{RootNamespace}.Views.{Controller}` | `MyApp.Views.Home.IndexView` |
| Model class | Plain DTO in `Models/` | `HomeViewModel` |
| Default route | `Home/Index` (configurable via `MvcApplicationBuilder`) | — |

### 5.3 How Views Map to Controllers

When a controller action calls `View(model)`, the framework resolves the view by:
1. Taking the controller name (e.g., `Home` from `HomeController`)
2. Taking the action name (e.g., `Index`)
3. Looking for a class named `IndexView` in namespace `*.Views.Home`

This means the `.cvw` file at `Views/Home/IndexView.cvw` generates exactly the class the framework expects.

---

## 6. Build System Integration

### 6.1 `ConsoleMVC.Framework.props` (Auto-Imported by NuGet)

```xml
<Project>
    <ItemGroup>
        <AdditionalFiles Include="Views/**/*.cvw" />
    </ItemGroup>
    <ItemGroup>
        <CompilerVisibleProperty Include="RootNamespace" />
    </ItemGroup>
</Project>
```

This file is packed into the NuGet package at `build/ConsoleMVC.Framework.props` and MSBuild auto-imports it into any project that references `ConsoleMVC.Framework`. It:

1. Globs all `.cvw` files under `Views/` as `AdditionalFiles` (making them visible to the source generator).
2. Exposes the `RootNamespace` property to the source generator.

### 6.2 Source Generator Packaging

The source generator DLL (`ConsoleMVC.Generators.dll`, targeting `netstandard2.0`) is packed into the NuGet package at `analyzers/dotnet/cs/`. The framework project references it with:

```xml
<ProjectReference Include="../ConsoleMVC.Generators/ConsoleMVC.Generators.csproj"
                  ReferenceOutputAssembly="false"
                  PrivateAssets="all" />
```

### 6.3 Consumer's `.csproj`

A consuming project only needs:

```xml
<PackageReference Include="ConsoleMVC.Framework" Version="1.0.2" />
```

Everything else (`.cvw` globbing, generator registration) is automatic via the `.props` file.

---

## 7. Complete Example: End-to-End Flow

### 7.1 Program.cs

```csharp
using ConsoleMVC.Mvc;

var builder = MvcApplication.CreateBuilder(args);
var app = builder.Build();
app.Run();
```

### 7.2 Models/HomeViewModel.cs

```csharp
namespace ConsoleMVC.App.Models;

public class HomeViewModel
{
    public string Title { get; set; } = "";
    public string Message { get; set; } = "";
    public List<string> MenuOptions { get; set; } = [];
}
```

### 7.3 Controllers/HomeController.cs

```csharp
using ConsoleMVC.App.Models;
using ConsoleMVC.Mvc;

namespace ConsoleMVC.App.Controllers;

public class HomeController : Controller
{
    public ActionResult Index()
    {
        var model = new HomeViewModel
        {
            Title = "Welcome to ConsoleMVC",
            Message = "A structured MVC framework for console applications.",
            MenuOptions = ["About", "Exit"]
        };
        return View(model);
    }

    public ActionResult About()
    {
        var model = new HomeViewModel
        {
            Title = "About ConsoleMVC",
            Message = "ConsoleMVC brings ASP.NET Core MVC patterns to console applications.",
            MenuOptions = ["Back to Home", "Exit"]
        };
        return View(model);
    }
}
```

### 7.4 Views/Home/IndexView.cvw

```
@model ConsoleMVC.App.Models.HomeViewModel

Console.WriteLine($"=== {Model.Title} ===");
Console.WriteLine();
Console.WriteLine(Model.Message);
Console.WriteLine();

for (var i = 0; i < Model.MenuOptions.Count; i++)
    Console.WriteLine($"  [{i + 1}] {Model.MenuOptions[i]}");

Console.WriteLine();
Console.Write("Select an option: ");
var input = Console.ReadLine()?.Trim();

return input switch
{
    "1" => NavigationResult.To("Home", "About"),
    "2" => NavigationResult.Quit(),
    _ => NavigationResult.To("Home", "Index")
};
```

### 7.5 Generated Output (What the Source Generator Produces)

For the above `IndexView.cvw`, the generator emits `Home_IndexView.g.cs`:

```csharp
// <auto-generated>
// Generated by ConsoleMVC ViewEngine from Home/IndexView.cvw
// Do not modify this file directly — edit the .cvw source instead.
// </auto-generated>
using ConsoleMVC.Mvc;

namespace ConsoleMVC.App.Views.Home
{
    public class IndexView : ConsoleView<ConsoleMVC.App.Models.HomeViewModel>
    {
        public override NavigationResult Render(ConsoleMVC.App.Models.HomeViewModel Model)
        {
            Console.WriteLine($"=== {Model.Title} ===");
            Console.WriteLine();
            Console.WriteLine(Model.Message);
            Console.WriteLine();

            for (var i = 0; i < Model.MenuOptions.Count; i++)
                Console.WriteLine($"  [{i + 1}] {Model.MenuOptions[i]}");

            Console.WriteLine();
            Console.Write("Select an option: ");
            var input = Console.ReadLine()?.Trim();

            return input switch
            {
                "1" => NavigationResult.To("Home", "About"),
                "2" => NavigationResult.Quit(),
                _ => NavigationResult.To("Home", "Index")
            };
        }
    }
}
```

---

## 8. Plugin Feature Requirements

### 8.1 Tier 1 — Essential (Must Have)

#### File Type Registration
- Register `.cvw` as a recognized file type in Rider.
- Assign a distinct icon for `.cvw` files in the project tree.
- Associate `.cvw` files with the plugin's custom language/editor.

#### Syntax Highlighting
- **Directive lines** (`@model`, `@using`): Highlight the `@keyword` portion as a keyword; highlight the type/namespace argument as a type reference.
- **Code body**: Full C# syntax highlighting (leverage Rider's existing C# lexer/parser for the code body region).
- **Comments** in the code body: Standard C# comment highlighting (`//`, `/* */`).

#### Code Completion (IntelliSense)
- **After `@model `**: Suggest fully-qualified type names from the project's compilation (all classes/structs/records).
- **After `@using `**: Suggest available namespaces from the project and its dependencies.
- **In the code body**: Provide full C# code completion including:
  - `Model.` — members of the type declared in `@model`.
  - `ViewData` — the `Dictionary<string, object?>` inherited from `ConsoleView`.
  - `NavigationResult.` — the static methods `To()`, `ToAction()`, `Quit()`.
  - All standard C# completions (local variables, `Console.*`, LINQ, etc.).
  - Types from `@using` namespaces and the implicit `ConsoleMVC.Mvc` namespace.

#### Error Highlighting
- Flag missing `@model` directive (the file will be silently skipped by the generator).
- Flag unknown types in `@model` directive.
- Flag unknown namespaces in `@using` directives.
- C# diagnostics in the code body (type errors, missing references, etc.).
- Warning if code body has no `return` statement (it must return `NavigationResult`).

### 8.2 Tier 2 — Important (Should Have)

#### Navigation
- **Ctrl+Click on `@model` type** -> navigate to the model class definition.
- **Ctrl+Click on types/methods in code body** -> navigate to their definitions.
- **Go to Related** from a controller action -> navigate to the corresponding `.cvw` view file.
- **Go to Related** from a `.cvw` file -> navigate to the controller action that uses it.
- **Gutter icons** on controller actions showing a link to the associated view.

#### Refactoring Support
- **Rename refactoring**: Renaming the model class should update `@model` directives in `.cvw` files.
- **Move refactoring**: Moving a model to a different namespace should update `@model` and `@using` directives.
- **Find Usages**: Finding usages of a model class should include references in `.cvw` files.

#### Live Templates / Code Snippets
- `cvw` — scaffold a new `.cvw` file with `@model` directive and a return statement.
- `navto` — insert `NavigationResult.To("$CONTROLLER$", "$ACTION$")` with completion for known controllers and actions.
- `navquit` — insert `NavigationResult.Quit()`.

#### File Templates
- "New ConsoleMVC View" file template that:
  - Prompts for the controller name and action name.
  - Creates the file at `Views/{Controller}/{Action}View.cvw`.
  - Pre-fills the `@model` directive if a matching controller action's `View(model)` call can be analyzed.

### 8.3 Tier 3 — Nice to Have

#### Structure View
- Show `.cvw` files in the Structure tool window with:
  - The `@model` type as a node.
  - The `@using` imports as children.
  - Key code body elements (return statements showing navigation targets).

#### Inspections and Quick-Fixes
- "Model type does not match controller's View() call" — if the controller passes a `FooModel` but the `.cvw` declares `@model BarModel`.
- "View file missing for controller action" — gutter warning on controller actions that call `View()` but have no corresponding `.cvw` file. Quick-fix: create the `.cvw` file.
- "Controller/action not found" in `NavigationResult.To("X", "Y")` — validate that the referenced controller and action exist.
- "Unused `@using` directive" — flag `@using` directives that are not referenced in the code body.

#### Breadcrumb / Tab Title
- Show `{Controller}/{Action}View` in the editor tab (e.g., `Home/IndexView`) rather than just the filename.

#### Formatting
- Apply Rider's C# code formatter to the code body section of `.cvw` files.
- Preserve directive formatting (no reformatting of `@model` / `@using` lines).

---

## 9. Plugin Architecture Guidance

### 9.1 Recommended Approach

The `.cvw` format is intentionally simple. The recommended implementation strategy:

1. **Custom Language & File Type**: Register a `CvwLanguage` extending `Language` and a `CvwFileType` with `.cvw` extension.

2. **Lexer**: A custom lexer that handles two modes:
   - **Directive mode** (at file start): Tokenize `@model`, `@using` as keywords, and their arguments as type/namespace references.
   - **C# mode** (after directives end): Delegate to Rider's C# lexer for the code body.

3. **PSI (Program Structure Interface)**: Build a PSI tree where:
   - The root is a `CvwFile` node.
   - Directive nodes (`CvwModelDirective`, `CvwUsingDirective`) contain type/namespace references.
   - The code body is a single node containing C# PSI elements.

4. **Virtual Document / Injection**: The most pragmatic approach for code completion and error checking in the code body may be **language injection** — constructing a virtual C# document that wraps the code body in the generated class structure (using the known transformation from Section 3.2) and injecting it into Rider's C# analysis engine. This is similar to how Rider handles Razor files.

5. **Reference Providers**: Create reference providers that resolve:
   - The type in `@model` directives to CLR type declarations.
   - The namespace in `@using` directives to namespace declarations.

### 9.2 Key IntelliJ/Rider APIs

- `com.intellij.lang.Language` — custom language registration.
- `com.intellij.openapi.fileTypes.FileType` — file type with icon.
- `com.intellij.lexer.Lexer` / `FlexAdapter` — lexing.
- `com.intellij.lang.ParserDefinition` — parsing into PSI.
- `com.intellij.psi.PsiFile` — file-level PSI node.
- `com.intellij.lang.injection.MultiHostInjector` — for injecting C# into the code body region.
- `com.intellij.codeInsight.completion.CompletionContributor` — custom completion.
- `com.intellij.codeInsight.navigation.actions.GotoDeclarationHandler` — Ctrl+Click navigation.
- `com.jetbrains.rider.ideaInterop.fileTypes.RiderLanguageFileType` — Rider-specific file type integration.
- ReSharper SDK (backend, C# side): `ILanguage`, `IProjectFileType`, `GeneratedDocumentService` — for backend analysis of injected C# code.

### 9.3 Rider Plugin Duality

Rider plugins typically have two parts:
- **Frontend (IntelliJ/Kotlin)**: UI, file type registration, editor integration, basic lexing/highlighting.
- **Backend (ReSharper/.NET)**: Deep C# analysis, type resolution, refactoring, navigation to CLR types.

For `.cvw` support, both sides will be needed:
- Frontend: File type, icon, basic syntax highlighting of directives, editor.
- Backend: C# code analysis of the code body, type resolution for `@model`, completion, inspections.

### 9.4 Similar Prior Art to Study

- **Razor support in Rider** — the closest analogue. Razor also has directives (`@model`, `@using`) and mixed-language content. Study how Rider's Razor plugin handles virtual document generation and C# injection.
- **Blazor support in Rider** — similar mixed C#/markup model.
- **Angular / Vue plugins for IntelliJ** — examples of language injection for TypeScript inside templates.

---

## 10. Reference: Full Source Generator Code

This is the complete source generator that transforms `.cvw` files. The plugin must understand this transformation to correctly provide IDE features.

```csharp
using System;
using System.Collections.Generic;
using System.IO;
using System.Linq;
using System.Text;
using Microsoft.CodeAnalysis;
using Microsoft.CodeAnalysis.Diagnostics;

#nullable enable

namespace ConsoleMVC.Generators;

[Generator]
public class ViewSourceGenerator : IIncrementalGenerator
{
    public void Initialize(IncrementalGeneratorInitializationContext context)
    {
        var viewFiles = context.AdditionalTextsProvider
            .Where(static file => file.Path.EndsWith(".cvw", StringComparison.OrdinalIgnoreCase));

        var rootNamespace = context.AnalyzerConfigOptionsProvider
            .Select(static (provider, _) =>
            {
                provider.GlobalOptions.TryGetValue("build_property.RootNamespace", out var ns);
                return ns ?? "ConsoleMVC";
            });

        var viewsWithNamespace = viewFiles.Combine(rootNamespace);

        context.RegisterSourceOutput(viewsWithNamespace, static (ctx, pair) =>
        {
            var (file, rootNs) = pair;

            var text = file.GetText(ctx.CancellationToken);
            if (text == null) return;

            var content = text.ToString();
            var lines = content.Split(new[] { "\r\n", "\n" }, StringSplitOptions.None);

            string? modelType = null;
            var usings = new List<string>();
            var codeStartIndex = 0;

            for (var i = 0; i < lines.Length; i++)
            {
                var trimmed = lines[i].Trim();

                if (string.IsNullOrWhiteSpace(trimmed))
                {
                    codeStartIndex = i + 1;
                    continue;
                }

                if (trimmed.StartsWith("@model "))
                {
                    modelType = trimmed.Substring("@model ".Length).Trim();
                    codeStartIndex = i + 1;
                    continue;
                }

                if (trimmed.StartsWith("@using "))
                {
                    usings.Add(trimmed.Substring("@using ".Length).Trim());
                    codeStartIndex = i + 1;
                    continue;
                }

                codeStartIndex = i;
                break;
            }

            if (modelType == null) return;

            var fileName = Path.GetFileNameWithoutExtension(file.Path);
            var pathParts = file.Path.Replace('\\', '/').Split('/');

            string? controllerName = null;
            for (var i = 0; i < pathParts.Length - 1; i++)
            {
                if (string.Equals(pathParts[i], "Views", StringComparison.OrdinalIgnoreCase)
                    && i + 1 < pathParts.Length - 1)
                {
                    controllerName = pathParts[i + 1];
                    break;
                }
            }

            if (controllerName == null) return;

            var codeLines = lines.Skip(codeStartIndex).ToArray();
            var codeBody = string.Join("\n",
                codeLines.Select(l => "        " + l));

            var usingBuilder = new StringBuilder();
            usingBuilder.AppendLine("using ConsoleMVC.Mvc;");
            foreach (var u in usings)
            {
                usingBuilder.AppendLine($"using {u};");
            }

            var source = $@"// <auto-generated>
// Generated by ConsoleMVC ViewEngine from {controllerName}/{fileName}.cvw
// Do not modify this file directly — edit the .cvw source instead.
// </auto-generated>
{usingBuilder}
namespace {rootNs}.Views.{controllerName}
{{
    public class {fileName} : ConsoleView<{modelType}>
    {{
        public override NavigationResult Render({modelType} Model)
        {{
{codeBody}
        }}
    }}
}}";

            ctx.AddSource($"{controllerName}_{fileName}.g.cs", source);
        });
    }
}
```

---

## 11. Reference: All Framework Types (Complete Source)

### NavigationResult.cs

```csharp
namespace ConsoleMVC.Mvc;

public class NavigationResult
{
    public string? Controller { get; init; }
    public string? Action { get; init; }
    public bool Exit { get; init; }

    public static NavigationResult To(string controller, string action)
        => new() { Controller = controller, Action = action };

    public static NavigationResult ToAction(string action)
        => new() { Action = action };

    public static NavigationResult Quit()
        => new() { Exit = true };
}
```

### ConsoleView.cs

```csharp
namespace ConsoleMVC.Mvc;

public abstract class ConsoleView
{
    public ViewDataDictionary ViewData { get; internal set; } = new();
    internal abstract NavigationResult RenderInternal(object? model);
}

public abstract class ConsoleView<TModel> : ConsoleView
{
    public abstract NavigationResult Render(TModel Model);

    internal override NavigationResult RenderInternal(object? model)
    {
        return Render((TModel)model!);
    }
}
```

### Controller.cs

```csharp
namespace ConsoleMVC.Mvc;

public abstract class Controller
{
    public RouteContext RouteContext { get; internal set; } = new();
    public ViewDataDictionary ViewData { get; } = new();

    protected ViewResult View(object? model = null)
    {
        return new ViewResult
        {
            ControllerName = GetControllerName(),
            ViewName = RouteContext.Action,
            Model = model,
            ViewData = ViewData
        };
    }

    protected RedirectToActionResult RedirectToAction(string action, string? controller = null)
    {
        return new RedirectToActionResult
        {
            ActionName = action,
            ControllerName = controller ?? GetControllerName()
        };
    }

    private string GetControllerName()
    {
        var name = GetType().Name;
        return name.EndsWith("Controller")
            ? name[..^"Controller".Length]
            : name;
    }
}
```

### ActionResult.cs

```csharp
namespace ConsoleMVC.Mvc;

public abstract class ActionResult { }
```

### ViewResult.cs

```csharp
namespace ConsoleMVC.Mvc;

public class ViewResult : ActionResult
{
    public string ControllerName { get; init; } = "";
    public string ViewName { get; init; } = "";
    public object? Model { get; init; }
    public ViewDataDictionary ViewData { get; init; } = new();
}
```

### RedirectToActionResult.cs

```csharp
namespace ConsoleMVC.Mvc;

public class RedirectToActionResult : ActionResult
{
    public string ControllerName { get; init; } = "";
    public string ActionName { get; init; } = "";
}
```

### RouteContext.cs

```csharp
namespace ConsoleMVC.Mvc;

public class RouteContext
{
    public string Controller { get; init; } = "Home";
    public string Action { get; init; } = "Index";
}
```

### ViewDataDictionary.cs

```csharp
namespace ConsoleMVC.Mvc;

public class ViewDataDictionary : Dictionary<string, object?> { }
```

### Router.cs (Discovery Logic)

```csharp
using System.Reflection;

namespace ConsoleMVC.Mvc;

public class Router
{
    private readonly Dictionary<string, Type> _controllers = new(StringComparer.OrdinalIgnoreCase);
    private readonly Dictionary<(string Controller, string Action), Type> _views = new();

    public void DiscoverAll(Assembly assembly)
    {
        DiscoverControllers(assembly);
        DiscoverViews(assembly);
    }

    private void DiscoverControllers(Assembly assembly)
    {
        var controllerBaseType = typeof(Controller);
        var controllerTypes = assembly.GetTypes()
            .Where(t => t is { IsAbstract: false, IsClass: true }
                        && controllerBaseType.IsAssignableFrom(t)
                        && t.Name.EndsWith("Controller"));

        foreach (var type in controllerTypes)
        {
            var name = type.Name[..^"Controller".Length];
            _controllers[name] = type;
        }
    }

    private void DiscoverViews(Assembly assembly)
    {
        var viewBaseType = typeof(ConsoleView);
        var viewTypes = assembly.GetTypes()
            .Where(t => t is { IsAbstract: false, IsClass: true }
                        && viewBaseType.IsAssignableFrom(t)
                        && t.Name.EndsWith("View"));

        foreach (var type in viewTypes)
        {
            var ns = type.Namespace ?? "";
            var segments = ns.Split('.');
            var viewsIndex = Array.IndexOf(segments, "Views");

            if (viewsIndex < 0 || viewsIndex + 1 >= segments.Length) continue;

            var controllerName = segments[viewsIndex + 1];
            var actionName = type.Name[..^"View".Length];
            _views[(controllerName, actionName)] = type;
        }
    }

    public Controller ResolveController(string controllerName) { /* ... */ }
    public MethodInfo ResolveAction(Type controllerType, string actionName) { /* ... */ }
    public ConsoleView ResolveView(string controllerName, string actionName) { /* ... */ }
}
```

### MvcApplication.cs (Event Loop)

```csharp
using System.Reflection;

namespace ConsoleMVC.Mvc;

public class MvcApplication
{
    private readonly Router _router = new();
    private RouteContext _currentRoute;

    internal MvcApplication(MvcApplicationBuilder builder)
    {
        _currentRoute = new RouteContext
        {
            Controller = builder.DefaultController,
            Action = builder.DefaultAction
        };
    }

    public static MvcApplicationBuilder CreateBuilder(string[] args)
        => new MvcApplicationBuilder();

    public void Run()
    {
        _router.DiscoverAll(Assembly.GetEntryAssembly()!);

        while (true)
        {
            var controller = _router.ResolveController(_currentRoute.Controller);
            controller.RouteContext = _currentRoute;
            var actionMethod = _router.ResolveAction(controller.GetType(), _currentRoute.Action);
            var result = (ActionResult)actionMethod.Invoke(controller, null)!;

            if (result is ViewResult viewResult)
            {
                var view = _router.ResolveView(viewResult.ControllerName, viewResult.ViewName);
                view.ViewData = viewResult.ViewData;
                Console.Clear();
                var navigation = view.RenderInternal(viewResult.Model);

                if (navigation.Exit) break;

                _currentRoute = new RouteContext
                {
                    Controller = navigation.Controller ?? _currentRoute.Controller,
                    Action = navigation.Action ?? _currentRoute.Action
                };
            }
            else if (result is RedirectToActionResult redirect)
            {
                _currentRoute = new RouteContext
                {
                    Controller = redirect.ControllerName,
                    Action = redirect.ActionName
                };
            }
        }
    }
}
```

### MvcApplicationBuilder.cs

```csharp
namespace ConsoleMVC.Mvc;

public class MvcApplicationBuilder
{
    public string DefaultController { get; set; } = "Home";
    public string DefaultAction { get; set; } = "Index";

    public MvcApplication Build() => new MvcApplication(this);
}
```

---

## 12. Reference: Example `.cvw` Files

### Views/Home/IndexView.cvw

```
@model ConsoleMVC.App.Models.HomeViewModel

Console.WriteLine($"=== {Model.Title} ===");
Console.WriteLine();
Console.WriteLine(Model.Message);
Console.WriteLine();

for (var i = 0; i < Model.MenuOptions.Count; i++)
    Console.WriteLine($"  [{i + 1}] {Model.MenuOptions[i]}");

Console.WriteLine();
Console.Write("Select an option: ");
var input = Console.ReadLine()?.Trim();

return input switch
{
    "1" => NavigationResult.To("Home", "About"),
    "2" => NavigationResult.Quit(),
    _ => NavigationResult.To("Home", "Index")
};
```

### Views/Home/AboutView.cvw

```
@model ConsoleMVC.App.Models.HomeViewModel

Console.WriteLine($"=== {Model.Title} ===");
Console.WriteLine();
Console.WriteLine(Model.Message);
Console.WriteLine();

for (var i = 0; i < Model.MenuOptions.Count; i++)
    Console.WriteLine($"  [{i + 1}] {Model.MenuOptions[i]}");

Console.WriteLine();
Console.Write("Select an option: ");
var input = Console.ReadLine()?.Trim();

return input switch
{
    "1" => NavigationResult.To("Home", "Index"),
    "2" => NavigationResult.Quit(),
    _ => NavigationResult.To("Home", "About")
};
```

### Example: View with `@using` Directive

```
@model MyApp.Models.DashboardViewModel
@using System.Globalization

Console.WriteLine($"Dashboard — {DateTime.Now.ToString("F", CultureInfo.InvariantCulture)}");
Console.WriteLine($"Welcome, {Model.UserName}!");
Console.WriteLine($"You have {Model.NotificationCount} notifications.");
Console.WriteLine();
Console.Write("Press Enter to continue...");
Console.ReadLine();

return NavigationResult.ToAction("Menu");
```

### Example: Minimal `.cvw` File

```
@model System.String

Console.WriteLine(Model);
return NavigationResult.Quit();
```

---

## 13. Project Development Guidelines

### 13.1 Git Commit Rules

- **NEVER** mention any AI assistant, copilot, or automated tool in commit messages, code comments, or any project file. All commits must read as if written by the developer directly.
- Commit author: `Mateusz Podeszwa <podinatubie@gmail.com>`
- Commit on every meaningful change so rollback is easy.
- Write clear, concise commit messages describing what changed and why.

### 13.2 Documentation & Continuity

This project is developed across multiple sessions. To ensure continuity between sessions:

- **`docs/DEVLOG.md`** — Append-only development log. Every significant implementation step, architectural decision, or known issue must be documented here with dates. This is the primary handoff document for the next session.
- **`docs/ARCHITECTURE.md`** — Describes the plugin's module structure, key classes, and how the frontend/backend split works. Update whenever the architecture changes.
- **`docs/TODO.md`** — Tracks what has been completed and what remains, organized by tier (1/2/3). Update after each commit.

When picking up work in a new session:
1. Read `docs/DEVLOG.md` to understand what was done and where it left off.
2. Read `docs/TODO.md` to see what's next.
3. Read `docs/ARCHITECTURE.md` to understand the current structure.

### 13.3 Implementation Strategy

- **Phase 1 (Frontend-only)**: File type registration, syntax highlighting, basic completion — all in Kotlin/IntelliJ side. No ReSharper backend yet.
- **Phase 2 (Backend)**: ReSharper/.NET backend for deep C# analysis, type resolution, refactoring. Only start after Phase 1 is working.
- Target: **Rider 2025.1+** (IntelliJ Platform 2025.1+).
- Build system: Gradle with `intellij-platform-gradle-plugin`.

### 13.4 Testing

- Every feature must be verified to work before committing.
- Include test files (sample `.cvw` files) in `src/test/testData/` for manual and automated testing.