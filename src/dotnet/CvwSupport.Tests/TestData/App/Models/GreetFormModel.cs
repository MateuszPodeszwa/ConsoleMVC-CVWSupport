using System.ComponentModel.DataAnnotations;

namespace ConsoleMVC.App.Models;

/// <summary>
/// Model bound from form data submitted by the Greet/Index view.
/// </summary>
public class GreetFormModel
{
    /// <summary>
    /// Gets or sets the name entered by the user.
    /// </summary>
    [Required, StringLength(30, ErrorMessage = "Please keep your name under 30 characters.")]
    public string Name { get; set; } = "";

    /// <summary>
    /// Gets or sets the user's favourite colour.
    /// </summary>
    [StringLength(20, ErrorMessage = "That colour name is too long (20 characters at most).")]
    public string Color { get; set; } = "";

    /// <summary>
    /// Gets or sets whether the greeting is shown in capitals (the &lt;checkbox&gt; in the form).
    /// </summary>
    public bool Shout { get; set; }
}
