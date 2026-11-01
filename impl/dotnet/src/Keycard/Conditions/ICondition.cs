namespace Keycard.Conditions;

/// <summary>A condition that can be written into a rule as the plain condition map the policy format defines.</summary>
public interface ICondition
{
    /// <summary>Returns the condition as the plain map a <c>PolicyDefinition</c> rule carries.</summary>
    IReadOnlyDictionary<string, object?> ToMap();
}
