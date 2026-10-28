namespace ConsoleMVC.App.Models;

/// <summary>
/// View model for the feature tour page, listing C-View Markup elements.
/// </summary>
public class FeaturesViewModel
{
    /// <summary>
    /// Gets or sets the rows shown in the feature table.
    /// </summary>
    public List<Feature> Features { get; set; } = [];
}

/// <summary>
/// One row of the feature table.
/// </summary>
/// <param name="Element">The markup element, e.g. "&lt;box&gt;".</param>
/// <param name="Purpose">What the element is for.</param>
public record Feature(string Element, string Purpose);
