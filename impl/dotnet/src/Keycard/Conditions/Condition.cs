namespace Keycard.Conditions;

/// <summary>
/// An untyped condition, written with field names as strings. Prefer
/// <see cref="Condition{TClaims}"/>, which checks field names and value types
/// at compile time; use this for conditions assembled dynamically.
/// </summary>
/// <example>
/// <code>
/// builder.Allow("Update", Subject.Named("Article"), Condition.Eq("owner_id", 1));
/// </code>
/// </example>
public sealed class Condition : ICondition
{
    private readonly IReadOnlyDictionary<string, object?> _map;

    private Condition(IReadOnlyDictionary<string, object?> map) => _map = map;

    public IReadOnlyDictionary<string, object?> ToMap() => _map;

    /// <summary>Wraps a hand-written condition map, such as <c>{ status: "open" }</c>.</summary>
    public static Condition From(IReadOnlyDictionary<string, object?> map) => new(map);

    public static Condition Eq(string field, object? value) => new(ConditionMaps.Field(field, "$eq", value));

    public static Condition Ne(string field, object? value) => new(ConditionMaps.Field(field, "$ne", value));

    public static Condition Gt(string field, double value) => new(ConditionMaps.Field(field, "$gt", value));

    public static Condition Gte(string field, double value) => new(ConditionMaps.Field(field, "$gte", value));

    public static Condition Lt(string field, double value) => new(ConditionMaps.Field(field, "$lt", value));

    public static Condition Lte(string field, double value) => new(ConditionMaps.Field(field, "$lte", value));

    /// <summary>Matches when the field's value is one of <paramref name="values"/>.</summary>
    public static Condition In(string field, params object?[] values) =>
        new(ConditionMaps.Field(field, "$in", values.ToList()));

    /// <summary>Matches when the field is an array containing <paramref name="value"/>.</summary>
    public static Condition Has(string field, object? value) => new(ConditionMaps.Field(field, "$has", value));

    /// <summary>Matches when the field's string form matches the <c>$substr</c> <paramref name="pattern"/>; see <see cref="Condition{TClaims}.Substr"/>.</summary>
    public static Condition Substr(string field, string pattern) => new(ConditionMaps.Field(field, "$substr", pattern));

    public static Condition And(params Condition[] conditions) => new(ConditionMaps.Group("$and", conditions));

    public static Condition Or(params Condition[] conditions) => new(ConditionMaps.Group("$or", conditions));

    public static Condition Not(Condition condition) => new(ConditionMaps.Not(condition));

    /// <summary>A custom (or built-in) operator applied to the whole subject.</summary>
    public static Condition Op(string operatorName, object? value) => new(ConditionMaps.Whole(operatorName, value));

    /// <summary>A custom (or built-in) operator scoped to a field, e.g. <c>Condition.Op("createdAt", "$withinDays", 30)</c>.</summary>
    public static Condition Op(string field, string operatorName, object? value) =>
        new(ConditionMaps.Field(field, operatorName, value));
}
