using Keycard.Errors;

namespace Keycard.Conditions;

/// <summary>
/// A typed way to write a rule's condition without hand-building nested maps.
/// Every factory returns a <see cref="Condition"/> that serializes as the
/// plain condition map the policy format defines.
/// </summary>
/// <example>
/// <code>
/// builder.Allow("Update", "Article", Condition.Or(
///     Condition.Eq("owner_id", 1),
///     Condition.In("status", "draft", "published")));
/// </code>
/// </example>
public sealed class Condition
{
    private readonly IReadOnlyDictionary<string, object?> _map;

    private Condition(IReadOnlyDictionary<string, object?> map) => _map = map;

    /// <summary>Returns the condition as the plain map a <c>PolicyDefinition</c> rule carries.</summary>
    public IReadOnlyDictionary<string, object?> ToMap() => _map;

    /// <summary>Wraps a hand-written condition map, such as <c>{ status: "open" }</c>.</summary>
    public static Condition From(IReadOnlyDictionary<string, object?> map) => new(map);

    public static Condition Eq(string field, object? value) => Field(field, "$eq", value);

    public static Condition Ne(string field, object? value) => Field(field, "$ne", value);

    public static Condition Gt(string field, double value) => Field(field, "$gt", value);

    public static Condition Gte(string field, double value) => Field(field, "$gte", value);

    public static Condition Lt(string field, double value) => Field(field, "$lt", value);

    public static Condition Lte(string field, double value) => Field(field, "$lte", value);

    /// <summary>Matches when the field's value is one of <paramref name="values"/>.</summary>
    public static Condition In(string field, params object?[] values) => Field(field, "$in", values.ToList());

    /// <summary>Matches when the field is an array containing <paramref name="value"/>.</summary>
    public static Condition Has(string field, object? value) => Field(field, "$has", value);

    /// <summary>
    /// Matches when the field's string form matches <paramref name="pattern"/>:
    /// <c>*</c> matches any run of characters, a leading <c>^</c> or trailing
    /// <c>$</c> anchors, and a backslash escapes the next character. An
    /// unanchored pattern matches anywhere in the value.
    /// </summary>
    public static Condition Substr(string field, string pattern) => Field(field, "$substr", pattern);

    public static Condition And(params Condition[] conditions) => Group("$and", conditions);

    public static Condition Or(params Condition[] conditions) => Group("$or", conditions);

    public static Condition Not(Condition condition) =>
        new(new Dictionary<string, object?> { ["$not"] = condition.ToMap() });

    /// <summary>A custom (or built-in) operator applied to the whole subject.</summary>
    public static Condition Op(string operatorName, object? value) =>
        new(new Dictionary<string, object?> { [Operator(operatorName)] = value });

    /// <summary>A custom (or built-in) operator scoped to a field, e.g. <c>Condition.Op("createdAt", "$withinDays", 30)</c>.</summary>
    public static Condition Op(string field, string operatorName, object? value) =>
        Field(field, Operator(operatorName), value);

    private static string Operator(string name)
    {
        if (!name.StartsWith('$') || name.Length < 2)
        {
            throw new PolicyArgumentException($"Invalid operator name \"{name}\": operator names MUST start with \"$\".");
        }

        return name;
    }

    private static Condition Group(string op, Condition[] conditions) =>
        new(new Dictionary<string, object?> { [op] = conditions.Select(c => (object?)c.ToMap()).ToList() });

    private static Condition Field(string field, string op, object? value)
    {
        var operand = new Dictionary<string, object?> { [op] = value };

        // A bare key starting with "$" would be read as an operator, so such a
        // field is addressed through the explicit $field tuple instead.
        return field.StartsWith('$')
            ? new Condition(new Dictionary<string, object?> { ["$field"] = new List<object?> { field, operand } })
            : new Condition(new Dictionary<string, object?> { [field] = operand });
    }
}
