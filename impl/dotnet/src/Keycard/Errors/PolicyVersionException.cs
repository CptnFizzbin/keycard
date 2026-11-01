namespace Keycard.Errors;

/// <summary>
/// Thrown when a <see cref="PolicyDefinition"/>'s <c>version</c> is invalid or
/// not supported by this implementation: a different MAJOR, or a MINOR higher
/// than it understands. PATCH never affects this decision.
/// </summary>
public class PolicyVersionException : Exception
{
    public PolicyVersionException(string message) : base(message) { }
}
