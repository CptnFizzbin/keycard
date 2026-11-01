namespace Keycard.Conditions;

/// <summary>
/// Returns whether <paramref name="subject"/> satisfies an operator given
/// operand <paramref name="value"/>. <paramref name="context"/> evaluates
/// nested conditions.
/// </summary>
public delegate bool OperatorResolver(object? subject, object? value, IOperatorContext context);
