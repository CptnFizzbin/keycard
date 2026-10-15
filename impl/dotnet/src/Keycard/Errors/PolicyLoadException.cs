namespace Keycard.Errors;

/// <summary>
/// Thrown when loading an invalid policy or operator set: a malformed rule
/// tuple or wildcard declaration, a both-sides-wildcarded rule carrying a
/// condition, a rule referencing an action, subject, or custom operator
/// outside a declared <c>meta</c> catalog, a <c>meta.operators</c> entry with
/// no registered operator, or two operators sharing a name.
/// </summary>
public class PolicyLoadException : Exception
{
    public PolicyLoadException(string message) : base(message) { }

    public PolicyLoadException(string message, Exception? innerException) : base(message, innerException) { }
}
