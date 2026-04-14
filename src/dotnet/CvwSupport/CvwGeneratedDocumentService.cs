using System;
using System.Collections.Generic;
using JetBrains.Application.Parts;
using JetBrains.ProjectModel;
using JetBrains.ReSharper.Psi;
using JetBrains.ReSharper.Psi.CSharp;
using JetBrains.ReSharper.Psi.ExtensionsAPI;
using JetBrains.ReSharper.Psi.Files;
using JetBrains.ReSharper.Psi.Impl.Shared;
using JetBrains.ReSharper.Psi.Parsing;
using JetBrains.ReSharper.Psi.Tree;
using JetBrains.ReSharper.Psi.Web.Generation;
using JetBrains.Text;
using JetBrains.Util;

namespace CvwSupport;

/// <summary>
/// Integrates .cvw virtual C# document generation into ReSharper's secondary
/// document analysis pipeline. This enables full C# IntelliSense, error checking,
/// navigation, and refactoring support within the code body of .cvw files.
///
/// ReSharper treats .cvw files as primary documents and generates a secondary C#
/// document (via CvwGeneratedDocumentFactory) that gets analyzed by the C# engine.
/// Results are projected back to the original .cvw positions via range mapping.
/// </summary>
[GeneratedDocumentService(typeof(CvwProjectFileType), Instantiation.DemandAnyThreadSafe)]
public class CvwGeneratedDocumentService : GeneratedDocumentServiceBase
{
    private readonly ILanguageManager _languageManager;

    public CvwGeneratedDocumentService(ILanguageManager languageManager)
    {
        _languageManager = languageManager;
    }

    public override IEnumerable<PsiLanguageType> GetSecondaryPsiLanguageTypes(IProject project)
    {
        yield return CSharpLanguage.Instance!;
    }

    public override bool IsSecondaryPsiLanguageType(IProject project, PsiLanguageType language)
    {
        return language.Is<CSharpLanguage>();
    }

    public override ISecondaryDocumentGenerationResult Generate(PrimaryFileModificationInfo modificationInfo)
    {
        var sourceFile = modificationInfo.SourceFile;
        var originalFile = modificationInfo.NewPsiFile;

        if (originalFile == null)
            return EmptyResult();

        var document = sourceFile.Document;
        var cvwText = document.GetText();
        var filePath = sourceFile.GetLocation().FullPath;

        // Determine root namespace from the project
        var rootNamespace = GetRootNamespace(sourceFile);

        // Generate the virtual C# document
        var factory = new CvwGeneratedDocumentFactory();
        var result = factory.Generate(cvwText, filePath, rootNamespace);

        if (result == null)
            return EmptyResult();

        // Build the range map for offset translation
        var rangeMap = GeneratedRangeMapFactory.CreateGeneratedRangeMap(originalFile);
        BuildRangeMap(rangeMap, result, cvwText);

        var rangeTranslator = new RangeTranslatorWithGeneratedRangeMap(rangeMap);

        // Get C# lexer factory for the generated text
        var csharpLanguage = CSharpLanguage.Instance!;
        var lexerFactory = _languageManager.GetService<ILexerFactory>(csharpLanguage);

        return new SecondaryDocumentGenerationResult(
            result.GeneratedText,
            csharpLanguage,
            rangeTranslator,
            lexerFactory);
    }

    /// <summary>
    /// Builds the range map that establishes bidirectional correspondence between
    /// character positions in the original .cvw file and the generated C# document.
    /// Maps directive arguments (@model type, @using namespaces) and code body lines.
    /// </summary>
    private static void BuildRangeMap(
        IGeneratedRangeMap rangeMap,
        CvwGeneratedDocumentFactory.GenerationResult result,
        string originalText)
    {
        var parser = result.Parser;

        // Map @model type argument to its occurrences in the generated document
        // (ConsoleView<ModelType> and Render(ModelType Model))
        if (parser.ModelTypeLocation != null)
        {
            var modelLoc = parser.ModelTypeLocation;
            foreach (var genOffset in result.GeneratedModelTypeOffsets)
            {
                var originalRange = new TreeTextRange<Original>(
                    new TreeOffset(modelLoc.Offset),
                    new TreeOffset(modelLoc.Offset + modelLoc.Length));
                var generatedRange = new TreeTextRange<Generated>(
                    new TreeOffset(genOffset),
                    new TreeOffset(genOffset + modelLoc.Length));

                rangeMap.Add(generatedRange, originalRange);
            }
        }

        // Map @using namespace arguments to their generated "using X;" directives
        for (var u = 0; u < parser.UsingLocations.Count && u < result.GeneratedUsingOffsets.Count; u++)
        {
            var usingLoc = parser.UsingLocations[u];
            var genOffset = result.GeneratedUsingOffsets[u];

            var originalRange = new TreeTextRange<Original>(
                new TreeOffset(usingLoc.Offset),
                new TreeOffset(usingLoc.Offset + usingLoc.Length));
            var generatedRange = new TreeTextRange<Generated>(
                new TreeOffset(genOffset),
                new TreeOffset(genOffset + usingLoc.Length));

            rangeMap.Add(generatedRange, originalRange);
        }

        // Map code body lines
        var codeBody = parser.CodeBody;
        if (string.IsNullOrEmpty(codeBody))
            return;

        var originalOffset = result.OriginalCodeBodyOffset;
        var generatedOffset = result.GeneratedCodeBodyOffset;
        var codeLines = codeBody.Split('\n');

        for (var i = 0; i < codeLines.Length; i++)
        {
            var line = codeLines[i];

            // Skip the 12-char indentation prefix in the generated document
            var genLineStart = generatedOffset + 12; // "            " prefix
            var lineLength = line.Length;

            if (lineLength > 0)
            {
                var originalRange = new TreeTextRange<Original>(
                    new TreeOffset(originalOffset),
                    new TreeOffset(originalOffset + lineLength));
                var generatedRange = new TreeTextRange<Generated>(
                    new TreeOffset(genLineStart),
                    new TreeOffset(genLineStart + lineLength));

                rangeMap.Add(generatedRange, originalRange);
            }

            // Advance past the line + newline in original
            originalOffset += lineLength;
            if (i < codeLines.Length - 1)
                originalOffset += 1; // \n

            // Advance past indentation + line + \r\n in generated (AppendLine uses Environment.NewLine)
            generatedOffset += 12 + lineLength + Environment.NewLine.Length;
        }
    }

    /// <summary>
    /// Gets the root namespace from the project, falling back to "ConsoleMVC".
    /// </summary>
    private static string GetRootNamespace(IPsiSourceFile sourceFile)
    {
        var project = sourceFile.GetProject();
        if (project != null)
        {
            // Use the project name as a reasonable approximation for the root namespace
            var projectName = project.Name;
            if (!string.IsNullOrEmpty(projectName))
                return projectName;
        }

        return "ConsoleMVC";
    }

    private static SecondaryDocumentGenerationResult EmptyResult()
    {
        return new SecondaryDocumentGenerationResult(
            string.Empty,
            CSharpLanguage.Instance!,
            new RangeTranslatorWithGeneratedRangeMap(
                GeneratedRangeMapFactory.CreateGeneratedRangeMap(null!)),
            null!);
    }
}
