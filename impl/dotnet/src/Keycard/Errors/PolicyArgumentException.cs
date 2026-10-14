namespace Keycard.Errors;

/// <summary>
/// Thrown at the call site when <see cref="PolicyBuilder"/> or a
/// <see cref="Keycard.Conditions.Condition"/> helper is given an invalid argument, such as a
/// conditional rule wildcarded on both action and subject.
/// </summary>
public class PolicyArgumentException : ArgumentException
{
    public PolicyArgumentException(string message) : base(message) { }
}
