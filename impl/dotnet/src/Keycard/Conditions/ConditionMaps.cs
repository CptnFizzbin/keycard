using Keycard.Errors;

namespace Keycard.Conditions;

/// <summary>Builds the plain condition maps shared by the untyped and typed condition factories.</summary>
internal static class ConditionMaps
{
    public static IReadOnlyDictionary<string, object?> Field(string field, string op, object? value)
    {
        var operand = new Dictionary<string, object?> { [AssertOperator(op)] = value };

        // A bare key starting with "$" would be read as an operator, so such a
        // field is addressed through the explicit $field tuple instead.
        return field.StartsWith('$')
            ? new Dictionary<string, object?> { ["$field"] = new List<object?> { field, operand } }
            : new Dictionary<string, object?> { [field] = operand };
    }

    public static IReadOnlyDictionary<string, object?> Whole(string op, object? value) =>
        new Dictionary<string, object?> { [AssertOperator(op)] = value };

    public static IReadOnlyDictionary<string, object?> Group(string op, IEnumerable<ICondition> conditions) =>
        new Dictionary<string, object?> { [op] = conditions.Select(c => (object?)c.ToMap()).ToList() };

    public static IReadOnlyDictionary<string, object?> Not(ICondition condition) =>
        new Dictionary<string, object?> { ["$not"] = condition.ToMap() };

    private static string AssertOperator(string name)
    {
        if (!name.StartsWith('$') || name.Length < 2)
        {
            throw new PolicyArgumentException($"Invalid operator name \"{name}\": operator names MUST start with \"$\".");
        }

        return name;
    }
}
