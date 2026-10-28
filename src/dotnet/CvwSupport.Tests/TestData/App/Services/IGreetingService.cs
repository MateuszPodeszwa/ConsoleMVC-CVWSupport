namespace ConsoleMVC.App.Services;

/// <summary>
/// Builds greetings. Registered in <c>Program.cs</c> with <c>builder.Services</c> and received by
/// <c>GreetController</c> through its constructor and by <c>Views/Greet/ResultView.cvw</c> through <c>@inject</c>.
/// </summary>
/// <example><code>var text = greetings.Greet("Ada", "teal"); // "Hello, Ada! Great choice — teal is …"</code></example>
public interface IGreetingService
{
    /// <summary>Gets how many greetings have been created since the application started.</summary>
    int Count { get; }

    /// <summary>
    /// Creates a greeting for <paramref name="name"/>, mentioning <paramref name="colour"/> when one is given.
    /// </summary>
    /// <param name="name">The person to greet.</param>
    /// <param name="colour">Their favourite colour, or <see langword="null"/>.</param>
    /// <returns>The greeting text.</returns>
    string Greet(string name, string? colour);
}
