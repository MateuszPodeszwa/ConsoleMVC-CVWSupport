namespace ConsoleMVC.App.Services;

/// <summary>
/// Settings for <see cref="GreetingService"/>, read from the <c>Greeting</c> section of <c>appsettings.json</c>
/// (or an environment variable such as <c>Greeting__Salutation=Hi</c>, or <c>--Greeting:Salutation=Hi</c>).
/// </summary>
/// <example><code>builder.Services.Configure&lt;GreetingOptions&gt;(builder.Configuration.GetSection(GreetingOptions.SectionName));</code></example>
public sealed class GreetingOptions
{
    /// <summary>The configuration section these options are read from.</summary>
    public const string SectionName = "Greeting";

    /// <summary>
    /// Gets or sets the word the greeting starts with.
    /// </summary>
    public string Salutation { get; set; } = "Hello";
}
