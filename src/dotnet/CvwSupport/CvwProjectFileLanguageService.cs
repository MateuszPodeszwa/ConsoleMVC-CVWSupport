using JetBrains.ProjectModel;
using JetBrains.ReSharper.Psi;
using JetBrains.ReSharper.Psi.Parsing;
using JetBrains.Text;
using JetBrains.UI.Icons;

namespace CvwSupport;

[ProjectFileType(typeof(CvwProjectFileType))]
public class CvwProjectFileLanguageService : ProjectFileLanguageService
{
    public CvwProjectFileLanguageService()
        : base(CvwProjectFileType.Instance!)
    {
    }

    public override ILexerFactory? GetMixedLexerFactory(
        ISolution solution,
        IBuffer buffer,
        IPsiSourceFile? sourceFile = null)
    {
        return null;
    }

    protected override PsiLanguageType PsiLanguageType =>
        CvwLanguage.Instance ?? (PsiLanguageType)UnknownLanguage.Instance!;

    public override IconId? Icon => null;
}
