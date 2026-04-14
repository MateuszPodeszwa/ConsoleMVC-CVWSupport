using JetBrains.ReSharper.Psi;

namespace CvwSupport;

[LanguageDefinition(Name)]
public class CvwLanguage : KnownLanguage
{
    public new const string Name = "CVW";

    public static readonly CvwLanguage Instance = null!;

    private CvwLanguage() : base(Name, "Console View") { }

    protected CvwLanguage(string name) : base(name) { }
    protected CvwLanguage(string name, string presentableName)
        : base(name, presentableName) { }
}
