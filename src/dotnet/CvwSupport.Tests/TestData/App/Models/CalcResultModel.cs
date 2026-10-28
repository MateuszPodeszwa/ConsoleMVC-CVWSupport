namespace ConsoleMVC.App.Models;

/// <summary>
/// View model for displaying a calculation result.
/// </summary>
public class CalcResultModel
{
    /// <summary>
    /// Gets or sets the human-readable expression (e.g. "2 + 3").
    /// </summary>
    public string Expression { get; set; } = "";

    /// <summary>
    /// Gets or sets the calculated result.
    /// </summary>
    public string Result { get; set; } = "";
}
