using System;
using System.Collections.Generic;
using System.Linq;

namespace CvwSupport;

/// <summary>
/// Parses a .cvw file into its constituent parts: model type, usings, and code body.
/// This mirrors the parsing logic of the ConsoleMVC source generator exactly.
/// </summary>
/// <summary>
/// Represents the location and value of a directive argument in the original .cvw text.
/// </summary>
public class DirectiveArgLocation
{
    /// <summary>Character offset in the original text where the argument starts.</summary>
    public int Offset { get; init; }

    /// <summary>Length of the argument text.</summary>
    public int Length { get; init; }

    /// <summary>The argument value (e.g., type name or namespace).</summary>
    public string Value { get; init; } = "";
}

public class CvwFileParser
{
    public string? ModelType { get; private set; }
    public List<string> Usings { get; } = new();
    public int CodeStartLine { get; private set; }
    public string CodeBody { get; private set; } = "";
    public string[] AllLines { get; private set; } = Array.Empty<string>();

    /// <summary>
    /// Offset in the original text where the code body begins.
    /// Used for range mapping between original and generated documents.
    /// </summary>
    public int CodeBodyOffset { get; private set; }

    /// <summary>
    /// Location of the @model type argument in the original text.
    /// Null if no @model directive is present.
    /// </summary>
    public DirectiveArgLocation? ModelTypeLocation { get; private set; }

    /// <summary>
    /// Locations of @using namespace arguments in the original text, in order.
    /// </summary>
    public List<DirectiveArgLocation> UsingLocations { get; } = new();

    public void Parse(string content)
    {
        AllLines = content.Split(new[] { "\r\n", "\n" }, StringSplitOptions.None);
        ModelType = null;
        Usings.Clear();
        UsingLocations.Clear();
        ModelTypeLocation = null;
        CodeStartLine = 0;
        CodeBodyOffset = 0;

        var charOffset = 0;

        for (var i = 0; i < AllLines.Length; i++)
        {
            var line = AllLines[i];
            var trimmed = line.Trim();
            var leadingSpaces = line.Length - line.TrimStart().Length;

            if (string.IsNullOrWhiteSpace(trimmed))
            {
                CodeStartLine = i + 1;
                charOffset += line.Length + 1; // +1 for \n
                continue;
            }

            if (trimmed.StartsWith("@model "))
            {
                var argText = trimmed.Substring("@model ".Length).Trim();
                var argStartInTrimmed = trimmed.IndexOf(argText, "@model ".Length, StringComparison.Ordinal);
                var argOffset = charOffset + leadingSpaces + argStartInTrimmed;

                ModelType = argText;
                ModelTypeLocation = new DirectiveArgLocation
                {
                    Offset = argOffset,
                    Length = argText.Length,
                    Value = argText
                };

                CodeStartLine = i + 1;
                charOffset += line.Length + 1;
                continue;
            }

            if (trimmed.StartsWith("@using "))
            {
                var argText = trimmed.Substring("@using ".Length).Trim();
                var argStartInTrimmed = trimmed.IndexOf(argText, "@using ".Length, StringComparison.Ordinal);
                var argOffset = charOffset + leadingSpaces + argStartInTrimmed;

                Usings.Add(argText);
                UsingLocations.Add(new DirectiveArgLocation
                {
                    Offset = argOffset,
                    Length = argText.Length,
                    Value = argText
                });

                CodeStartLine = i + 1;
                charOffset += line.Length + 1;
                continue;
            }

            // First non-directive, non-blank line = code body start
            CodeStartLine = i;
            CodeBodyOffset = charOffset;
            break;
        }

        // If we exhausted all lines without finding code, CodeBodyOffset is at the end
        if (CodeStartLine >= AllLines.Length)
        {
            CodeBodyOffset = content.Length;
            CodeBody = "";
        }
        else
        {
            CodeBodyOffset = charOffset;
            var codeLines = AllLines.Skip(CodeStartLine).ToArray();
            CodeBody = string.Join("\n", codeLines);
        }
    }
}
