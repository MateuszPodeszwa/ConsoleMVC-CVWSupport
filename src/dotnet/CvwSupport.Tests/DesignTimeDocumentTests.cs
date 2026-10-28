using System.Reflection;
using System.Text.Json;
using CvwSupport;
using Microsoft.CodeAnalysis;
using Microsoft.CodeAnalysis.CSharp;

namespace CvwSupport.Tests;

/// <summary>
/// Tests of the design-time C# document the backend builds for each view: it must compile against ConsoleMVC with the
/// template's models, so the IDE shows no false errors, and every mapped piece must be the view's own text.
/// </summary>
public class DesignTimeDocumentTests
{
    private static readonly string TestRoot = FindTestRoot();

    private static string FindTestRoot()
    {
        for (var directory = new DirectoryInfo(AppContext.BaseDirectory); directory != null; directory = directory.Parent)
        {
            if (File.Exists(Path.Combine(directory.FullName, "CvwSupport.Tests.csproj")))
                return directory.FullName;
        }

        throw new InvalidOperationException("CvwSupport.Tests.csproj not found above " + AppContext.BaseDirectory);
    }

    private static string AppFolder => Path.Combine(TestRoot, "TestData", "App");

    /// <summary>Every view of the template, by its path under TestData/App.</summary>
    public static TheoryData<string> TemplateViews()
    {
        var data = new TheoryData<string>();
        foreach (var file in Directory.GetFiles(Path.Combine(FindTestRoot(), "TestData", "App"), "*.cvw", SearchOption.AllDirectories).OrderBy(f => f))
            data.Add(Path.GetRelativePath(Path.Combine(FindTestRoot(), "TestData", "App"), file).Replace('\\', '/'));
        return data;
    }

    private static CvwGeneratedDocumentFactory.GenerationResult Generate(string relativePath) =>
        new CvwGeneratedDocumentFactory().Generate(File.ReadAllText(Path.Combine(AppFolder, relativePath)), "/work/Shop/" + relativePath, "ConsoleMVC.App");

    /// <summary>Compiles C# sources with the template's models against ConsoleMVC and returns the errors.</summary>
    private static List<string> CompileErrors(params string[] documents)
    {
        var compilation = TestCompilation.Create(documents.Select((d, i) => CSharpSyntaxTree.ParseText(d, path: $"view{i}.g.cs")));
        return compilation.GetDiagnostics()
            .Where(d => d.Severity == DiagnosticSeverity.Error)
            .Select(d => d.ToString())
            .ToList();
    }

    /// <summary>Fails with the compiler's errors, and the documents, when they do not compile.</summary>
    private static void AssertCompiles(params string[] documents)
    {
        var errors = CompileErrors(documents);
        Assert.True(errors.Count == 0, string.Join("\n", errors) + "\n\n" + string.Join("\n----\n", documents));
    }

    [Theory]
    [MemberData(nameof(TemplateViews))]
    public void TheDesignTimeDocument_Compiles(string view)
    {
        var result = Generate(view);
        AssertCompiles(result.GeneratedText);
    }

    [Fact]
    public void AllTheTemplatesViews_CompileTogether()
    {
        var documents = TemplateViews().Cast<object[]>().Select(row => Generate((string)row[0]).GeneratedText).ToArray();
        AssertCompiles(documents);
    }

    [Theory]
    [MemberData(nameof(TemplateViews))]
    public void EveryMappedPiece_IsTheViewsOwnText(string view)
    {
        var text = File.ReadAllText(Path.Combine(AppFolder, view));
        var result = Generate(view);
        Assert.NotEmpty(result.Segments);
        foreach (var segment in result.Segments)
            Assert.Equal(text.Substring(segment.OriginalOffset, segment.Length), result.GeneratedText.Substring(segment.GeneratedOffset, segment.Length));
    }

    [Fact]
    public void AMistakeInTheViewsCSharp_IsAnErrorAtItsPlace()
    {
        const string view = "@model ConsoleMVC.App.Models.HomeViewModel\n<text>@Model.Titel</text>\n";
        var result = new CvwGeneratedDocumentFactory().Generate(view, "/work/Shop/Views/Home/BrokenView.cvw", "ConsoleMVC.App");
        var errors = CompileErrors(result.GeneratedText);
        Assert.Contains(errors, e => e.Contains("Titel", StringComparison.Ordinal));

        var segment = result.Segments.Single(s => view.Substring(s.OriginalOffset, s.Length) == "Model.Titel");
        Assert.Equal(view.IndexOf("Model.Titel", StringComparison.Ordinal), segment.OriginalOffset);
    }

    [Fact]
    public void MarkupIsLeftOut_AndExpressionsBecomeStatements()
    {
        const string view = "<box title=\"Hi @Model\">\n    <text>@(1 + 2) and @Model.ToString()</text>\n</box>\nreturn NavigationResult.Quit();\n";
        var result = new CvwGeneratedDocumentFactory().Generate(view, "/work/Shop/Views/Home/AView.cvw", "ConsoleMVC.App");
        Assert.DoesNotContain("<box", result.GeneratedText);
        Assert.Contains("_ = Model;", result.GeneratedText);
        Assert.Contains("_ = (1 + 2);", result.GeneratedText);
        Assert.Contains("_ = Model.ToString();", result.GeneratedText);
        Assert.DoesNotContain("return default!;", result.GeneratedText);
        AssertCompiles(result.GeneratedText);
    }

    [Fact]
    public void TheKnownElements_AreTheSchemas()
    {
        var schemaFile = Path.GetFullPath(Path.Combine(TestRoot, "..", "..", "main", "resources", "schema", "cvw-schema.json"));
        using var schema = JsonDocument.Parse(File.ReadAllText(schemaFile));
        var names = schema.RootElement.GetProperty("elements").EnumerateArray().Select(e => e.GetProperty("name").GetString()!).ToHashSet();
        Assert.Equal(names.OrderBy(n => n), CvwMarkupMask.KnownElements.OrderBy(n => n));

        var voids = schema.RootElement.GetProperty("elements").EnumerateArray()
            .Where(e => e.GetProperty("content").GetString() == "void").Select(e => e.GetProperty("name").GetString()!).OrderBy(n => n);
        var field = typeof(CvwMarkupMask).GetField("VoidElements", BindingFlags.NonPublic | BindingFlags.Static)!;
        Assert.Equal(voids, ((HashSet<string>)field.GetValue(null)!).OrderBy(n => n));
    }
}
