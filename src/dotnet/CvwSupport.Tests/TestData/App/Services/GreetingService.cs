using Microsoft.Extensions.Options;

namespace ConsoleMVC.App.Services;

/// <summary>
/// The default <see cref="IGreetingService"/>. Registered as a singleton, so <see cref="Count"/> is kept for the
/// lifetime of the application.
/// </summary>
/// <param name="options">The <c>Greeting</c> settings from <c>appsettings.json</c>.</param>
/// <example><code>builder.Services.AddSingleton&lt;IGreetingService, GreetingService&gt;();</code></example>
public sealed class GreetingService(IOptions<GreetingOptions> options) : IGreetingService
{
    /// <inheritdoc />
    public int Count { get; private set; }

    /// <inheritdoc />
    public string Greet(string name, string? colour)
    {
        Count++;

        var greeting = $"{options.Value.Salutation}, {name}!";
        if (!string.IsNullOrWhiteSpace(colour))
            greeting += $" Great choice — {colour} is a wonderful colour.";

        return greeting;
    }
}
