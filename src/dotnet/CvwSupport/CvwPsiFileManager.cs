using System;
using JetBrains.Application.Parts;
using JetBrains.DocumentModel;
using JetBrains.ProjectModel;
using JetBrains.ReSharper.Psi;

namespace CvwSupport;

/// <summary>
/// Manages the PSI representation of .cvw files on the ReSharper backend.
/// This component is responsible for:
/// 1. Detecting when .cvw files are opened/modified
/// 2. Generating the virtual C# document via CvwGeneratedDocumentFactory
/// 3. Providing the generated document to ReSharper's C# analysis engine
///
/// Note: Full integration with ReSharper's ISecondaryDocumentGenerationService
/// requires deeper SDK integration that depends on the specific Rider version.
/// This class provides the foundational infrastructure for that integration.
/// </summary>
[SolutionComponent(Instantiation.DemandAnyThreadSafe)]
public class CvwPsiFileManager
{
    private readonly CvwGeneratedDocumentFactory _factory = new();

    /// <summary>
    /// Generates a virtual C# document for the given .cvw source file.
    /// Returns null if the file doesn't have a valid @model directive.
    /// </summary>
    public CvwGeneratedDocumentFactory.GenerationResult? GetGeneratedDocument(
        IPsiSourceFile sourceFile,
        string rootNamespace = "ConsoleMVC")
    {
        var document = sourceFile.Document;
        var text = document.GetText();
        var path = sourceFile.GetLocation().FullPath;

        return _factory.Generate(text, path, rootNamespace);
    }

    /// <summary>
    /// Extracts the model type from a .cvw file without full generation.
    /// Useful for quick lookups (e.g., tooltip, completion filtering).
    /// </summary>
    public string? GetModelType(string cvwContent)
    {
        var parser = new CvwFileParser();
        parser.Parse(cvwContent);
        return parser.ModelType;
    }
}
