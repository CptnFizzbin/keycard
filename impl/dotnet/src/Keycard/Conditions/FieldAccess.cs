using System.Collections;
using System.Collections.Concurrent;
using System.Reflection;
using System.Text.Json.Serialization;

namespace Keycard.Conditions;

internal static class FieldAccess
{
    // A null entry caches a miss, so an absent field isn't re-resolved on every evaluation.
    private static readonly ConcurrentDictionary<(Type, string), Func<object, object?>?> Accessors = new();

    /// <summary>
    /// Evaluates <paramref name="condition"/> against the <paramref name="fieldName"/>
    /// field of <paramref name="subject"/>: a map key, or else a public property
    /// or field (matched by its exact name, its <see cref="JsonPropertyNameAttribute"/>,
    /// or its camelCased name). A missing field (or null subject) is absence,
    /// not a type issue: it satisfies only a bare <c>$ne</c>.
    /// </summary>
    public static bool Check(object? subject, string fieldName, object? condition, IOperatorContext ctx)
    {
        // A second narrowing step is a malformed condition shape, not a data
        // mismatch, so it's diagnosed rather than silently returning false.
        if (!ctx.CanNarrowField)
        {
            ctx.ReportTypeIssue(fieldName,
                "v0 supports only top-level field access - a field condition can't itself narrow into another field");
            return false;
        }

        if (subject is null) return IsBareNe(condition);

        if (TryReadMapKey(subject, fieldName, out var present, out var mapValue))
        {
            return present ? ctx.ResolveFieldSubcondition(mapValue, condition) : IsBareNe(condition);
        }

        var accessor = Accessors.GetOrAdd((subject.GetType(), fieldName), key => FindAccessor(key.Item1, key.Item2));
        if (accessor is null) return IsBareNe(condition);

        object? value;
        try
        {
            value = accessor(subject);
        }
        catch (TargetInvocationException e)
        {
            ctx.ReportTypeIssue(fieldName, $"reading the field threw {e.InnerException}");
            return false;
        }

        return ctx.ResolveFieldSubcondition(value, condition);
    }

    private static bool TryReadMapKey(object subject, string key, out bool present, out object? value)
    {
        present = false;
        value = null;

        if (subject is IDictionary map)
        {
            present = map.Contains(key);
            if (present) value = map[key];
            return true;
        }

        if (subject is IReadOnlyDictionary<string, object?> readOnly)
        {
            present = readOnly.TryGetValue(key, out value);
            return true;
        }

        return false;
    }

    private static Func<object, object?>? FindAccessor(Type type, string name)
    {
        if (name.Length == 0) return null;

        const BindingFlags flags = BindingFlags.Public | BindingFlags.Instance;
        var properties = type.GetProperties(flags).Where(p => p.GetIndexParameters().Length == 0 && p.CanRead).ToList();
        var fields = type.GetFields(flags);

        var property =
            properties.FirstOrDefault(p => p.Name == name)
            ?? properties.FirstOrDefault(p => p.GetCustomAttribute<JsonPropertyNameAttribute>()?.Name == name)
            ?? properties.FirstOrDefault(p => Camel(p.Name) == name);
        if (property is not null) return property.GetValue;

        var field =
            fields.FirstOrDefault(f => f.Name == name)
            ?? fields.FirstOrDefault(f => f.GetCustomAttribute<JsonPropertyNameAttribute>()?.Name == name)
            ?? fields.FirstOrDefault(f => Camel(f.Name) == name);
        return field is not null ? field.GetValue : null;
    }

    private static string Camel(string name) => char.ToLowerInvariant(name[0]) + name[1..];

    // TODO: decide whether { $not: { $eq: x } } on a missing field should also
    // be true, since $not carries the same "exact negation" contract as $ne.
    private static bool IsBareNe(object? condition) =>
        condition is IDictionary<string, object?> { Count: 1 } map && map.ContainsKey("$ne");
}
