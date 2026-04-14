using System;
using System.Collections.Generic;
using System.Linq;

namespace CvwSupport;

/// <summary>
/// Parses a .cvw file into its constituent parts: model type, usings, and code body.
/// This mirrors the parsing logic of the ConsoleMVC source generator exactly.
/// </summary>
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

    public void Parse(string content)
    {
        AllLines = content.Split(new[] { "\r\n", "\n" }, StringSplitOptions.None);
        ModelType = null;
        Usings.Clear();
        CodeStartLine = 0;
        CodeBodyOffset = 0;

        var charOffset = 0;

        for (var i = 0; i < AllLines.Length; i++)
        {
            var trimmed = AllLines[i].Trim();

            if (string.IsNullOrWhiteSpace(trimmed))
            {
                CodeStartLine = i + 1;
                charOffset += AllLines[i].Length + 1; // +1 for \n
                continue;
            }

            if (trimmed.StartsWith("@model "))
            {
                ModelType = trimmed.Substring("@model ".Length).Trim();
                CodeStartLine = i + 1;
                charOffset += AllLines[i].Length + 1;
                continue;
            }

            if (trimmed.StartsWith("@using "))
            {
                Usings.Add(trimmed.Substring("@using ".Length).Trim());
                CodeStartLine = i + 1;
                charOffset += AllLines[i].Length + 1;
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
