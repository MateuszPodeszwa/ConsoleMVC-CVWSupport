using Microsoft.CodeAnalysis;
using Microsoft.CodeAnalysis.CSharp;

namespace CvwSupport.Tests;

/// <summary>
/// A compilation of the template's C# (models, services, resources) against ConsoleMVC and .NET, as the template's own
/// build compiles it, to which views or design-time documents are added.
/// </summary>
internal static class TestCompilation
{
    /// <summary>The SDK's global usings for a console project with ImplicitUsings, as the template builds.</summary>
    private const string ImplicitUsings = "global using System; global using System.Collections.Generic; global using System.IO; " +
                                          "global using System.Linq; global using System.Net.Http; global using System.Threading; global using System.Threading.Tasks;";

    /// <summary>Creates the compilation, with <paramref name="extra"/> trees added.</summary>
    public static CSharpCompilation Create(IEnumerable<SyntaxTree>? extra = null)
    {
        var appFolder = Path.Combine(FindTestRoot(), "TestData", "App");
        var trees = Directory.GetFiles(appFolder, "*.cs", SearchOption.AllDirectories)
            .Select(f => CSharpSyntaxTree.ParseText(File.ReadAllText(f), path: f))
            .Append(CSharpSyntaxTree.ParseText(ImplicitUsings, path: "ImplicitUsings.g.cs"))
            .Concat(extra ?? []);

        var references = ((string)AppContext.GetData("TRUSTED_PLATFORM_ASSEMBLIES")!).Split(Path.PathSeparator)
            .Concat(Directory.GetFiles(AppContext.BaseDirectory, "*.dll"))
            .Where(path => !Path.GetFileName(path).StartsWith("CvwSupport", StringComparison.Ordinal))
            .GroupBy(Path.GetFileName)
            .Select(group => MetadataReference.CreateFromFile(group.First()));

        return CSharpCompilation.Create("App", trees, references,
            new CSharpCompilationOptions(OutputKind.DynamicallyLinkedLibrary, nullableContextOptions: NullableContextOptions.Enable));
    }

    private static string FindTestRoot()
    {
        for (var directory = new DirectoryInfo(AppContext.BaseDirectory); directory != null; directory = directory.Parent)
        {
            if (File.Exists(Path.Combine(directory.FullName, "CvwSupport.Tests.csproj")))
                return directory.FullName;
        }

        throw new InvalidOperationException("CvwSupport.Tests.csproj not found");
    }
}
