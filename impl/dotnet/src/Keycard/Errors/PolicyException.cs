namespace Keycard.Errors;

/// <summary>Thrown by <see cref="Policy.Require"/> when a check is denied.</summary>
public class PolicyException : Exception
{
    public PolicyException(string message) : base(message) { }

    public PolicyException(string message, Exception? innerException) : base(message, innerException) { }
}
