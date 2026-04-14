using JetBrains.ProjectModel;

namespace CvwSupport;

[ProjectFileTypeDefinition(Name)]
public class CvwProjectFileType : KnownProjectFileType
{
    public new const string Name = "CVW";
    public const string CvwExtension = ".cvw";

    public new static readonly CvwProjectFileType Instance = null!;

    private CvwProjectFileType()
        : base(Name, "Console View", new[] { CvwExtension })
    {
    }

    protected CvwProjectFileType(string name) : base(name) { }
    protected CvwProjectFileType(string name, string presentableName)
        : base(name, presentableName) { }
}
