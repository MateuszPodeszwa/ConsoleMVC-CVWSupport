using System.Collections.Immutable;
using System.Reflection;
using System.Text.RegularExpressions;
using CvwSupport;
using Microsoft.CodeAnalysis;
using Microsoft.CodeAnalysis.CSharp;
using Microsoft.CodeAnalysis.Diagnostics;
using Microsoft.CodeAnalysis.Text;

namespace CvwSupport.Tests;

/// <summary>
/// Compares the design-time documents with what ConsoleMVC's own source generator (from the ConsoleMVC.Framework
/// package, run here as the build runs it) makes of the same views: the same namespace (plus .DesignTime), base class
/// and model type, and both compile together without clashing.
/// </summary>
public class RealGeneratorTests
{
    private const string Root = "ConsoleMVC.App";
    private const string ProjectFolder = "/work/Shop/";

    private static readonly string AppFolder = Path.Combine(FindTestRoot(), "TestData", "App");

    private static string FindTestRoot()
    {
        for (var directory = new DirectoryInfo(AppContext.BaseDirectory); directory != null; directory = directory.Parent)
        {
            if (File.Exists(Path.Combine(directory.FullName, "CvwSupport.Tests.csproj")))
                return directory.FullName;
        }

        throw new InvalidOperationException("CvwSupport.Tests.csproj not found");
    }

    private static string[] Views => Directory.GetFiles(AppFolder, "*.cvw", SearchOption.AllDirectories)
        .Select(f => Path.GetRelativePath(AppFolder, f).Replace('\\', '/')).OrderBy(f => f).ToArray();

    /// <summary>A view as the build passes it to the generator.</summary>
    private sealed class ViewText(string path, string text) : AdditionalText
    {
        public override string Path { get; } = path;

        public override SourceText GetText(CancellationToken cancellationToken = default) => SourceText.From(text);
    }

    private sealed class Options(Dictionary<string, string> values) : AnalyzerConfigOptions
    {
        public override bool TryGetValue(string key, out string value) => values.TryGetValue(key, out value!);
    }

    private sealed class OptionsProvider(AnalyzerConfigOptions global) : AnalyzerConfigOptionsProvider
    {
        private static readonly AnalyzerConfigOptions Empty = new Options(new Dictionary<string, string>());

        public override AnalyzerConfigOptions GlobalOptions => global;

        public override AnalyzerConfigOptions GetOptions(SyntaxTree tree) => Empty;

        public override AnalyzerConfigOptions GetOptions(AdditionalText textFile) => Empty;
    }

    /// <summary>Runs the real generator over the template's views and returns its sources and diagnostics.</summary>
    private static (List<SyntaxTree> Generated, ImmutableArray<Diagnostic> Diagnostics, CSharpCompilation Compilation) RunGenerator()
    {
        var generatorPath = typeof(RealGeneratorTests).Assembly.GetCustomAttributes<AssemblyMetadataAttribute>()
            .Single(a => a.Key == "ConsoleMvcGenerator").Value!;
        var generators = Assembly.LoadFrom(generatorPath).GetTypes()
            .Where(t => !t.IsAbstract && typeof(IIncrementalGenerator).IsAssignableFrom(t) && t.GetCustomAttribute<GeneratorAttribute>() != null)
            .Select(t => ((IIncrementalGenerator)Activator.CreateInstance(t)!).AsSourceGenerator())
            .ToList();
        Assert.NotEmpty(generators);

        var texts = Views.Select(v => (AdditionalText)new ViewText(ProjectFolder + v, File.ReadAllText(Path.Combine(AppFolder, v)))).ToList();
        var options = new OptionsProvider(new Options(new Dictionary<string, string>
        {
            ["build_property.RootNamespace"] = Root,
            ["build_property.ProjectDir"] = ProjectFolder
        }));

        var compilation = TestCompilation.Create();
        var driver = CSharpGeneratorDriver.Create(generators, texts, CSharpParseOptions.Default, options);
        driver.RunGeneratorsAndUpdateCompilation(compilation, out var output, out var diagnostics);
        var generated = output.SyntaxTrees.Except(compilation.SyntaxTrees).ToList();
        return (generated, diagnostics, (CSharpCompilation)output);
    }

    [Fact]
    public void TheRealGenerator_CompilesTheTemplate()
    {
        var (generated, diagnostics, compilation) = RunGenerator();
        Assert.NotEmpty(generated);
        Assert.Empty(diagnostics.Where(d => d.Severity == DiagnosticSeverity.Error));
        Assert.Empty(compilation.GetDiagnostics().Where(d => d.Severity == DiagnosticSeverity.Error).Select(d => d.ToString()));
    }

    [Fact]
    public void EachDesignTimeDocument_HasTheGeneratorsNamespaceBaseClassAndModel()
    {
        var (generated, _, _) = RunGenerator();
        foreach (var view in Views)
        {
            var className = Path.GetFileNameWithoutExtension(view);
            var designTime = new CvwGeneratedDocumentFactory().Generate(File.ReadAllText(Path.Combine(AppFolder, view)), ProjectFolder + view, Root).GeneratedText;

            var real = generated.Select(t => t.ToString())
                .Single(t => Regex.IsMatch(t, $@"public partial class {className} : ") && Regex.IsMatch(t, "#line \\d+ \"[^\"]*" + Regex.Escape(view) + "\""));
            // The generator maps the model type back to the view with #line directives inside the declaration
            var plain = Regex.Replace(real, @"^[ \t]*#line[^\n]*\n", "", RegexOptions.Multiline);
            var realNamespace = Regex.Match(plain, @"^namespace ([\w.]+)", RegexOptions.Multiline).Groups[1].Value;
            var realBase = Normalise(Regex.Match(plain, $@"public partial class {className} :([^{{]+)\{{").Groups[1].Value);

            Assert.NotEmpty(realNamespace);
            Assert.StartsWith("Console", realBase);
            Assert.Equal(realNamespace + ".DesignTime", Regex.Match(designTime, @"^namespace ([\w.]+)", RegexOptions.Multiline).Groups[1].Value);
            Assert.Equal(realBase, Normalise(Regex.Match(designTime, $@"public class {className} :([^{{]+)\{{").Groups[1].Value));
        }
    }

    [Fact]
    public void TheDesignTimeDocuments_AndTheGeneratedViews_CompileTogether()
    {
        var (_, _, compilation) = RunGenerator();
        var designTime = Views.Select(v => CSharpSyntaxTree.ParseText(
            new CvwGeneratedDocumentFactory().Generate(File.ReadAllText(Path.Combine(AppFolder, v)), ProjectFolder + v, Root).GeneratedText, path: v + ".g.cs"));
        var errors = compilation.AddSyntaxTrees(designTime).GetDiagnostics().Where(d => d.Severity == DiagnosticSeverity.Error).Select(d => d.ToString()).ToList();
        Assert.True(errors.Count == 0, string.Join("\n", errors));
    }

    /// <summary>A base class without global:: and namespace prefixes, so the generator's and ours compare.</summary>
    private static string Normalise(string type) =>
        Regex.Replace(type, @"global::|ConsoleMVC\.Mvc\.|\s", "");
}
