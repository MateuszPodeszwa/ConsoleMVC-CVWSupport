using JetBrains.Application.BuildScript.Application.Zones;
using JetBrains.ReSharper.Feature.Services;
using JetBrains.ReSharper.Psi;

namespace CvwSupport;

[ZoneDefinition]
public interface ICvwSupportZone : IPsiLanguageZone, IRequire<ICodeEditingZone>;

[ZoneMarker]
public class ZoneMarker : IRequire<ICvwSupportZone>;
