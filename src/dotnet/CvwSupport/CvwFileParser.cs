using System;
using System.Collections.Generic;
using System.Text.RegularExpressions;

namespace CvwSupport;

/// <summary>
/// The location and value of a directive's argument in the original .cvw text.
/// </summary>
/// <example><code>var type = parser.ModelTypeLocation?.Value; // "Shop.Models.HomeViewModel"</code></example>
public class DirectiveArgLocation
{
    /// <summary>Character offset in the original text where the argument starts.</summary>
    public int Offset { get; init; }

    /// <summary>Length of the argument text.</summary>
    public int Length { get; init; }

    /// <summary>The argument value (e.g., type name or namespace).</summary>
    public string Value { get; init; } = "";
}

/// <summary>An <c>@inject Type Name</c> directive: the service's type and the property's name, with their locations.</summary>
/// <example><code>foreach (var inject in parser.Injects) Console.WriteLine($"{inject.Type.Value} {inject.Name.Value}");</code></example>
public class InjectLocation
{
    /// <summary>The service's type, such as <c>IStringLocalizer&lt;SharedResource&gt;</c>.</summary>
    public DirectiveArgLocation Type { get; init; } = new();

    /// <summary>The property's name, such as <c>L</c>.</summary>
    public DirectiveArgLocation Name { get; init; } = new();
}

/// <summary>
/// Reads the directives of a .cvw view (<c>@model</c>, <c>@using</c>, <c>@inject</c>; <c>@layout</c> and
/// <c>@refresh</c> need nothing in the virtual document), with where their arguments are, as ConsoleMVC's generator
/// does: a directive is a line starting with it, outside markup. The body is classified by <see cref="CvwMarkupMask"/>.
/// </summary>
/// <example>
/// <code>
/// var parser = new CvwFileParser();
/// parser.Parse("@model Shop.Models.Cart\n&lt;text&gt;@Model.Count&lt;/text&gt;");
/// parser.ModelType; // "Shop.Models.Cart"
/// </code>
/// </example>
public class CvwFileParser
{
    private static readonly Regex DirectiveLine = new(@"^[ \t]*@(model|using|inject)[ \t]+([^\r\n]*?)[ \t]*\r?$", RegexOptions.Multiline);

    private static readonly Regex InjectArguments = new(@"^(.+?)\s+([\p{L}_][\p{L}\p{Nd}_]*)$");

    /// <summary>The model type, or <see langword="null"/> when there is no <c>@model</c> (the model is then <c>object</c>).</summary>
    public string? ModelType => ModelTypeLocation?.Value;

    /// <summary>The namespaces of the <c>@using</c> directives, in order.</summary>
    public List<string> Usings { get; } = new();

    /// <summary>Location of the @model type argument, or null.</summary>
    public DirectiveArgLocation? ModelTypeLocation { get; private set; }

    /// <summary>Locations of the @using namespace arguments, in order.</summary>
    public List<DirectiveArgLocation> UsingLocations { get; } = new();

    /// <summary>The <c>@inject</c> directives, in order.</summary>
    public List<InjectLocation> Injects { get; } = new();

    /// <summary>The kind of each character of the view: C#, markup or an expression.</summary>
    public CvwCharKind[] Kinds { get; private set; } = Array.Empty<CvwCharKind>();

    /// <summary>Whether the view has a <c>&lt;body /&gt;</c>, which makes it a layout.</summary>
    public bool HasBodyPlaceholder { get; private set; }

    /// <summary>Parses a view's text.</summary>
    /// <param name="content">The view's text.</param>
    public void Parse(string content)
    {
        ModelTypeLocation = null;
        Usings.Clear();
        UsingLocations.Clear();
        Injects.Clear();
        Kinds = CvwMarkupMask.Classify(content);
        HasBodyPlaceholder = Regex.IsMatch(content, @"<body\s*/>");

        foreach (Match match in DirectiveLine.Matches(content))
        {
            var keyword = match.Groups[1];
            var argument = match.Groups[2];
            // Only directives outside markup: their characters are marked as markup by the mask
            if (argument.Length == 0 || Kinds[keyword.Index] != CvwCharKind.Markup)
                continue;

            var location = new DirectiveArgLocation { Offset = argument.Index, Length = argument.Length, Value = argument.Value };
            switch (keyword.Value)
            {
                case "model":
                    ModelTypeLocation ??= location;
                    break;
                case "using":
                    Usings.Add(argument.Value);
                    UsingLocations.Add(location);
                    break;
                case "inject":
                    var parts = InjectArguments.Match(argument.Value);
                    if (!parts.Success)
                        break;
                    Injects.Add(new InjectLocation
                    {
                        Type = new DirectiveArgLocation { Offset = argument.Index + parts.Groups[1].Index, Length = parts.Groups[1].Length, Value = parts.Groups[1].Value },
                        Name = new DirectiveArgLocation { Offset = argument.Index + parts.Groups[2].Index, Length = parts.Groups[2].Length, Value = parts.Groups[2].Value }
                    });
                    break;
            }
        }
    }
}
