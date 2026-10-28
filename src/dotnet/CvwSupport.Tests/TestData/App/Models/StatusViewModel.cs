namespace ConsoleMVC.App.Models;

/// <summary>
/// View model for the status page, which refreshes every second.
/// </summary>
public class StatusViewModel
{
    /// <summary>
    /// Gets or sets when the page was drawn.
    /// </summary>
    public DateTime Time { get; set; }

    /// <summary>
    /// Gets or sets which terminal the page is shown on: this console, or a remote session (dotnet run -- --ConsoleMVC:Serve=2323).
    /// </summary>
    public string Session { get; set; } = "";

    /// <summary>
    /// Gets or sets how many of the services are up.
    /// </summary>
    public int Healthy { get; set; }

    /// <summary>
    /// Gets or sets how many services there are.
    /// </summary>
    public int Total { get; set; }

    /// <summary>
    /// Gets or sets the load over the last minute, oldest first, drawn by the page's sparkline.
    /// </summary>
    public List<int> Load { get; set; } = [];

    /// <summary>
    /// Gets or sets each service's response time, drawn by the page's bar chart.
    /// </summary>
    public List<ServiceTiming> Timings { get; set; } = [];
}

/// <summary>
/// One service on the status page.
/// </summary>
/// <param name="Name">The service's name.</param>
/// <param name="Milliseconds">How long it took to answer.</param>
public record ServiceTiming(string Name, int Milliseconds);
