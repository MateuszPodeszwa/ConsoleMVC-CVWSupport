using System;
using System.Collections.Generic;

namespace CvwSupport;

/// <summary>What a character of a view is, for building the virtual C# document.</summary>
public enum CvwCharKind
{
    /// <summary>C#: copied to the virtual document as it is.</summary>
    Code,

    /// <summary>Markup, a comment or a directive line: left out of the virtual document.</summary>
    Markup,

    /// <summary>The <c>@</c> of an expression such as <c>@Model.Name</c>.</summary>
    ExpressionTransition,

    /// <summary>The C# of an expression after its <c>@</c>: written as <c>_ = expression;</c>.</summary>
    Expression,

    /// <summary>The <c>@</c> before a block of code such as <c>@{ }</c> or <c>@foreach</c>: left out.</summary>
    BlockTransition
}

/// <summary>
/// Tells the C# of a <c>.cvw</c> view from its C-View Markup, following ConsoleMVC's generator: at the top level and in
/// <c>@{ }</c> blocks the text is C#, and <c>&lt;tag</c> starts markup only where a statement may begin and the tag is
/// a known element; inside an element every <c>&lt;tag</c> is markup, <c>@</c> starts an expression or a block of code,
/// and <c>&lt;/</c> closes. The same rules as the plugin's MarkupParser.kt and the VS Code extension's MarkupParser.js.
/// </summary>
/// <example>
/// <code>
/// var kinds = CvwMarkupMask.Classify("&lt;text&gt;Hi @Model.Name&lt;/text&gt;");
/// // kinds[0] == Markup ('&lt;'), kinds[10] == ExpressionTransition ('@'), kinds[11] == Expression ('M')
/// </code>
/// </example>
public sealed class CvwMarkupMask
{
    /// <summary>The elements of C-View Markup (ConsoleMVC's schema/cvw-schema.json); a test keeps them in step.</summary>
    public static readonly ISet<string> KnownElements = new HashSet<string>(StringComparer.Ordinal)
    {
        "text", "box", "separator", "br", "table", "row", "cell", "menu", "option", "input", "select", "radio",
        "choice", "checkbox", "number", "date", "errors", "breadcrumbs", "shortcuts", "progress", "spinner",
        "bar-chart", "bar", "sparkline", "scroll", "markdown", "columns", "column", "partial", "body"
    };

    /// <summary>The elements without content, written as <c>&lt;name /&gt;</c>.</summary>
    private static readonly HashSet<string> VoidElements = new(StringComparer.Ordinal)
    {
        "separator", "br", "input", "checkbox", "number", "date", "errors", "breadcrumbs", "shortcuts", "progress",
        "spinner", "bar", "sparkline", "markdown", "partial", "body"
    };

    private static readonly HashSet<string> CodeKeywords = new(StringComparer.Ordinal)
    {
        "if", "foreach", "for", "while", "switch", "lock", "using", "do", "try"
    };

    private static readonly string[] Directives = { "model", "using", "inject", "layout", "refresh" };

    private const int MaxDepth = 64;

    private readonly string _text;
    private readonly CvwCharKind[] _kinds;
    private int _position;
    private int _depth;

    private CvwMarkupMask(string text)
    {
        _text = text;
        _kinds = new CvwCharKind[text.Length];
    }

    /// <summary>Classifies every character of a view.</summary>
    /// <param name="text">The view's text.</param>
    /// <returns>One kind per character.</returns>
    public static CvwCharKind[] Classify(string text)
    {
        var mask = new CvwMarkupMask(text);
        mask.Code(atTopLevel: true, stopAtBrace: false);
        return mask._kinds;
    }

    private char At(int index) => index >= 0 && index < _text.Length ? _text[index] : '\0';

    private void Paint(int start, int end, CvwCharKind kind)
    {
        for (var i = Math.Max(0, start); i < Math.Min(_text.Length, end); i++)
            _kinds[i] = kind;
    }

    // ---------------------------------------------------------------- C#

    private void Code(bool atTopLevel, bool stopAtBrace)
    {
        var last = '\0';
        var braces = 0;

        while (_position < _text.Length)
        {
            var c = _text[_position];

            if (char.IsWhiteSpace(c))
            {
                _position++;
                continue;
            }

            // A directive line (@model, @using …) is not C#
            if (c == '@' && atTopLevel && IsFirstOnLine(_position) && IsDirective(_position))
            {
                var start = _position;
                SkipToLineEnd();
                Paint(start, _position, CvwCharKind.Markup);
                continue;
            }

            if (c == '#' && IsFirstOnLine(_position))
            {
                SkipToLineEnd();
                continue;
            }

            if (c == '@' && At(_position + 1) == '*')
            {
                var start = _position;
                SkipDelimited("*@");
                Paint(start, _position, CvwCharKind.Markup);
                continue;
            }

            if (SkipCSharpCommentOrString())
            {
                if (At(_position - 1) == '"' || At(_position - 1) == '\'')
                    last = '"';
                continue;
            }

            if (c == '<' && (last == '\0' || last == ';' || last == '{' || last == '}' || last == ':'))
            {
                if (string.CompareOrdinal(_text, _position, "<!--", 0, 4) == 0)
                {
                    var start = _position;
                    SkipDelimited("-->");
                    Paint(start, _position, CvwCharKind.Markup);
                    continue;
                }

                var name = PeekTagName(_position);
                if (name != null && KnownElements.Contains(name))
                {
                    Element(name, parentName: null);
                    last = ';';
                    continue;
                }
            }

            if (c == '{')
            {
                braces++;
            }
            else if (c == '}')
            {
                if (braces == 0 && stopAtBrace)
                    return;
                braces--;
            }

            last = c;
            _position++;
        }
    }

    /// <summary>Skips a C# comment or string; returns whether it did.</summary>
    private bool SkipCSharpCommentOrString()
    {
        var c = At(_position);
        var next = At(_position + 1);

        if (c == '/' && next == '/')
        {
            SkipToLineEnd();
            return true;
        }

        if (c == '/' && next == '*')
        {
            SkipDelimited("*/");
            return true;
        }

        var prefix = 0;
        if ((c == '$' && next == '@') || (c == '@' && next == '$'))
            prefix = 2;
        else if (c == '$' || c == '@')
            prefix = 1;

        var quote = At(_position + prefix);
        if (quote != '"' && !(quote == '\'' && prefix == 0))
            return false;

        var verbatim = prefix > 0 && (c == '@' || next == '@');
        _position += prefix + 1;
        while (_position < _text.Length)
        {
            var ch = _text[_position];
            if (!verbatim && ch == '\\')
            {
                _position += 2;
                continue;
            }

            if (!verbatim && ch == '\n')
                return true;

            if (ch == quote)
            {
                if (verbatim && At(_position + 1) == quote)
                {
                    _position += 2;
                    continue;
                }

                _position++;
                return true;
            }

            _position++;
        }

        _position = Math.Min(_position, _text.Length);
        return true;
    }

    // ---------------------------------------------------------------- markup

    private void Element(string name, string? parentName)
    {
        var start = _position;
        _position += 1 + name.Length;
        _depth++;
        try
        {
            if (_depth > MaxDepth)
            {
                Paint(start, _text.Length, CvwCharKind.Markup);
                _position = _text.Length;
                return;
            }

            var finished = StartTag(start, out var selfClosing);
            if (finished && !selfClosing && !VoidElements.Contains(name))
                Content(name);
        }
        finally
        {
            _depth--;
        }
    }

    /// <summary>Reads the start tag, painting it as markup and its expressions as C#.</summary>
    private bool StartTag(int start, out bool selfClosing)
    {
        selfClosing = false;
        while (_position < _text.Length)
        {
            var c = _text[_position];
            if (string.CompareOrdinal(_text, _position, "/>", 0, 2) == 0)
            {
                _position += 2;
                selfClosing = true;
                PaintTag(start, _position);
                return true;
            }

            if (c == '>')
            {
                _position++;
                PaintTag(start, _position);
                return true;
            }

            if (c == '<')
            {
                PaintTag(start, _position);
                return false;
            }

            if (c == '"' || c == '\'')
            {
                QuotedValue(c);
                continue;
            }

            if (c == '@' && (At(_position + 1) == '(' || IsIdentifierStart(At(_position + 1))) && !char.IsLetterOrDigit(At(_position - 1)))
            {
                // An unquoted value such as title=@Model.Title
                _position = ExpressionEnd(_position);
                continue;
            }

            _position++;
        }

        PaintTag(start, _position);
        return false;
    }

    /// <summary>Paints a tag as markup, leaving the expressions already marked inside it.</summary>
    private void PaintTag(int start, int end)
    {
        for (var i = start; i < end && i < _text.Length; i++)
        {
            if (_kinds[i] == CvwCharKind.Code)
                _kinds[i] = CvwCharKind.Markup;
        }
    }

    /// <summary>Skips a quoted attribute value, marking its expressions.</summary>
    private void QuotedValue(char quote)
    {
        var from = ++_position;
        while (_position < _text.Length)
        {
            var c = _text[_position];
            if (c == quote)
            {
                _position++;
                return;
            }

            if (c == '\n' && _text.IndexOf('<', from, _position - from) >= 0)
                return; // a new tag: the quote was forgotten

            if (c == '@' && At(_position + 1) == '@')
            {
                _position += 2;
                continue;
            }

            if (c == '@' && (At(_position + 1) == '(' || IsIdentifierStart(At(_position + 1))) && !char.IsLetterOrDigit(At(_position - 1)))
            {
                _position = ExpressionEnd(_position);
                continue;
            }

            _position++;
        }
    }

    /// <summary>Marks the expression whose @ is at <paramref name="at"/> and returns its end.</summary>
    private int ExpressionEnd(int at)
    {
        int end;
        if (At(at + 1) == '(')
        {
            end = BalancedEnd(at + 1, '(', ')');
        }
        else
        {
            end = at + 1;
            while (IsIdentifierPart(At(end))) end++;
            for (;;)
            {
                if ((At(end) == '.' && IsIdentifierStart(At(end + 1))) || (At(end) == '?' && At(end + 1) == '.' && IsIdentifierStart(At(end + 2))))
                {
                    end += At(end) == '?' ? 2 : 1;
                    while (IsIdentifierPart(At(end))) end++;
                }
                else if (At(end) == '(')
                {
                    end = BalancedEnd(end, '(', ')');
                }
                else if (At(end) == '[')
                {
                    end = BalancedEnd(end, '[', ']');
                }
                else
                {
                    break;
                }
            }
        }

        Paint(at, at + 1, CvwCharKind.ExpressionTransition);
        Paint(at + 1, end, CvwCharKind.Expression);
        return end;
    }

    private void Content(string name)
    {
        var contentStart = _position;
        while (_position < _text.Length)
        {
            var c = _text[_position];

            if (c == '<' && At(_position + 1) == '/')
            {
                var closing = PeekTagName(_position + 1);
                var close = _text.IndexOf('>', _position);
                var end = close < 0 ? _text.Length : close + 1;
                Paint(_position, end, CvwCharKind.Markup);
                _position = end;
                if (closing == name)
                    return;
                continue;
            }

            if (c == '<' && string.CompareOrdinal(_text, _position, "<!--", 0, 4) == 0)
            {
                var start = _position;
                SkipDelimited("-->");
                Paint(start, _position, CvwCharKind.Markup);
                continue;
            }

            if (c == '<' && PeekTagName(_position) is { } child)
            {
                Element(child, name);
                continue;
            }

            if (c == '@')
            {
                At_(name);
                continue;
            }

            Paint(_position, _position + 1, CvwCharKind.Markup);
            _position++;
        }
    }

    /// <summary>An @ in content: an escape, a comment, an expression or a block of code.</summary>
    private void At_(string elementName)
    {
        var at = _position;
        var next = At(at + 1);

        if (next == '@')
        {
            Paint(at, at + 2, CvwCharKind.Markup);
            _position += 2;
            return;
        }

        if (next == '*')
        {
            SkipDelimited("*@");
            Paint(at, _position, CvwCharKind.Markup);
            return;
        }

        if (next == '{')
        {
            Paint(at, at + 1, CvwCharKind.BlockTransition);
            _position += 2;
            CodeBlockBody();
            return;
        }

        // The @ of an e-mail address (me@example.com) is text, as in the generator
        if (at > 0 && char.IsLetterOrDigit(_text[at - 1]))
        {
            Paint(at, at + 1, CvwCharKind.Markup);
            _position++;
            return;
        }

        var wordEnd = at + 1;
        while (IsIdentifierPart(At(wordEnd))) wordEnd++;
        var word = wordEnd > at + 1 ? _text.Substring(at + 1, wordEnd - at - 1) : null;

        if (word != null && CodeKeywords.Contains(word))
        {
            Paint(at, at + 1, CvwCharKind.BlockTransition);
            _position = wordEnd;
            ControlFlow();
            return;
        }

        if (word != null || next == '(')
        {
            _position = ExpressionEnd(at);
            return;
        }

        Paint(at, at + 1, CvwCharKind.Markup);
        _position++;
    }

    /// <summary>Reads <c>(…) { … }</c> after @if, @foreach and the like, with any else / catch that follows.</summary>
    private void ControlFlow()
    {
        for (;;)
        {
            while (_position < _text.Length && _text[_position] != '{')
            {
                if (_text[_position] == '(')
                    _position = BalancedEnd(_position, '(', ')');
                else if (_text[_position] == '<')
                    return;
                else
                    _position++;
            }

            if (_position >= _text.Length)
                return;

            _position++;
            CodeBlockBody();

            var after = _position;
            while (char.IsWhiteSpace(At(after))) after++;
            var keyword = after;
            while (char.IsLetter(At(keyword))) keyword++;
            var word = _text.Substring(after, keyword - after);
            if (word != "else" && word != "catch" && word != "finally")
                return;
            _position = keyword;
        }
    }

    private void CodeBlockBody()
    {
        Code(atTopLevel: false, stopAtBrace: true);
        if (At(_position) == '}')
            _position++;
    }

    // ---------------------------------------------------------------- helpers

    private int BalancedEnd(int from, char open, char close)
    {
        var saved = _position;
        var nesting = 0;
        _position = from;
        try
        {
            while (_position < _text.Length)
            {
                var c = _text[_position];
                if ((c == '"' || c == '\'' || c == '@' || c == '$') && SkipCSharpCommentOrString())
                    continue;

                _position++;
                if (c == open)
                    nesting++;
                else if (c == close && --nesting == 0)
                    return _position;
            }

            return _text.Length;
        }
        finally
        {
            _position = saved;
        }
    }

    private string? PeekTagName(int at)
    {
        var start = at + 1;
        if (!char.IsLetter(At(start)) || At(start) > 'z')
            return null;
        var end = start + 1;
        while (char.IsLetterOrDigit(At(end)) || At(end) == '-' || At(end) == '_') end++;
        return _text.Substring(start, end - start);
    }

    private bool IsDirective(int at)
    {
        foreach (var directive in Directives)
        {
            if (string.CompareOrdinal(_text, at + 1, directive, 0, directive.Length) == 0 && !IsIdentifierPart(At(at + 1 + directive.Length)))
                return true;
        }

        return false;
    }

    private bool IsFirstOnLine(int index)
    {
        for (var i = index - 1; i >= 0 && _text[i] != '\n'; i--)
        {
            if (!char.IsWhiteSpace(_text[i]))
                return false;
        }

        return true;
    }

    private void SkipToLineEnd()
    {
        var end = _text.IndexOf('\n', _position);
        _position = end < 0 ? _text.Length : end;
    }

    private void SkipDelimited(string close)
    {
        var end = _text.IndexOf(close, _position + 2, StringComparison.Ordinal);
        _position = end < 0 ? _text.Length : end + close.Length;
    }

    private static bool IsIdentifierStart(char c) => char.IsLetter(c) || c == '_';

    private static bool IsIdentifierPart(char c) => char.IsLetterOrDigit(c) || c == '_';
}
