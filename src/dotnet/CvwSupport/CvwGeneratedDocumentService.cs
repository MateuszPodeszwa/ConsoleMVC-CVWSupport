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
/// navigation, and refactoring support for the C# of .cvw files (code blocks, conditions and @expressions in markup).
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

        // Build the range map for offset translation
        var rangeMap = GeneratedRangeMapFactory.CreateGeneratedRangeMap(originalFile);
        BuildRangeMap(rangeMap, result);

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
    /// Builds the range map between the view and the virtual C# document: every piece of the view copied into the
    /// document (directive arguments, the C# of the body, the expressions in markup) maps back to its place.
    /// </summary>
    private static void BuildRangeMap(IGeneratedRangeMap rangeMap, CvwGeneratedDocumentFactory.GenerationResult result)
    {
        foreach (var segment in result.Segments)
        {
            var originalRange = new TreeTextRange<Original>(
                new TreeOffset(segment.OriginalOffset),
                new TreeOffset(segment.OriginalOffset + segment.Length));
            var generatedRange = new TreeTextRange<Generated>(
                new TreeOffset(segment.GeneratedOffset),
                new TreeOffset(segment.GeneratedOffset + segment.Length));

            rangeMap.Add(generatedRange, originalRange);
        }
    }

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
