namespace Keycard.Conditions;

/// <summary>
/// Passed to every <see cref="OperatorResolver"/>, built-in and custom alike,
/// so an operator can evaluate nested conditions the way <c>$and</c>,
/// <c>$or</c>, and <c>$not</c> do.
/// </summary>
public interface IOperatorContext
{
    /// <summary>
    /// Evaluates <paramref name="condition"/> against <paramref name="subject"/>,
    /// preserving whether this point in the tree may still narrow into a field.
    /// </summary>
    bool ResolveSubcondition(object? subject, object? condition);

    /// <summary>
    /// Evaluates <paramref name="condition"/> against a subject already narrowed
    /// by one field access, disabling any further field narrowing beneath it.
    /// </summary>
    bool ResolveFieldSubcondition(object? subject, object? condition);

    /// <summary>Whether a field condition may still narrow at this point in the tree; v0 permits exactly one level.</summary>
    bool CanNarrowField { get; }

    /// <summary>
    /// Reports a genuine type issue (a malformed operand, a wrong-typed subject)
    /// for <paramref name="operatorName"/>, never an ordinary non-match.
    /// </summary>
    void ReportTypeIssue(string operatorName, string message);
}
