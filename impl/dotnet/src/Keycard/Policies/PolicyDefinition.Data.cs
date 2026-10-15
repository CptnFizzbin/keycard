using System.Collections;
using Keycard.Errors;

namespace Keycard;

public sealed partial class PolicyDefinition
{
    /// <summary>
    /// Reads a definition from plain data in the policy document shape: maps
    /// (<see cref="IDictionary{TKey,TValue}"/> of string keys), lists, strings,
    /// numbers, booleans, and <c>null</c>. This is the shape every format
    /// package (JSON, YAML) reads a document into; <paramref name="document"/>
    /// is not modified.
    /// </summary>
    /// <exception cref="PolicyLoadException">The document is malformed.</exception>
    public static PolicyDefinition FromData(object? document)
    {
        var doc = AsMap(document) ?? throw new PolicyLoadException("A policy document must be an object.");

        var definition = new PolicyDefinition
        {
            Version = doc.GetValueOrDefault("version") as string
                ?? throw new PolicyLoadException("A policy document must declare a string version."),
            Name = doc.GetValueOrDefault("name") as string,
            Description = doc.GetValueOrDefault("description") as string,
        };

        if (doc.GetValueOrDefault("rules") is { } rules)
        {
            definition.Rules = (AsList(rules) ?? throw new PolicyLoadException("A policy's rules must be an array."))
                .Select(ReadRule).ToList();
        }

        if (doc.GetValueOrDefault("meta") is { } meta)
        {
            definition.Meta = ReadMeta(AsMap(meta) ?? throw new PolicyLoadException("A policy's meta must be an object."));
        }

        if (doc.GetValueOrDefault("tests") is { } tests)
        {
            definition.Tests = (AsList(tests) ?? throw new PolicyLoadException("A policy's tests must be an array."))
                .Select(ReadSuite).ToList();
        }

        return definition;
    }

    /// <summary>
    /// Returns this definition as plain data in the policy document shape: a
    /// new map of maps, lists, strings, numbers, booleans, and <c>null</c> that
    /// a format package writes out.
    /// </summary>
    public IReadOnlyDictionary<string, object?> ToData()
    {
        var document = new Dictionary<string, object?> { ["version"] = Version };
        if (Name is not null) document["name"] = Name;
        if (Description is not null) document["description"] = Description;
        if (Meta is not null) document["meta"] = WriteMeta(Meta);

        document["rules"] = Rules.Select(r => (object?)WriteRule(r)).ToList();

        if (Tests is not null) document["tests"] = Tests.Select(t => (object?)WriteSuite(t)).ToList();
        return document;
    }

    private static PolicyMeta ReadMeta(IReadOnlyDictionary<string, object?> meta) => new()
    {
        // WildcardToken.Of reads an explicit null as "disabled", so only a key that is
        // present is converted; an absent one stays undeclared.
        AnyAction = meta.TryGetValue("anyAction", out var anyAction) ? WildcardToken.Of(anyAction) : null,
        AnySubject = meta.TryGetValue("anySubject", out var anySubject) ? WildcardToken.Of(anySubject) : null,
        Actions = Strings(meta, "actions"),
        Subjects = Strings(meta, "subjects"),
        Operators = Strings(meta, "operators"),
        Application = meta.GetValueOrDefault("application"),
    };

    private static Dictionary<string, object?> WriteMeta(PolicyMeta meta)
    {
        var data = new Dictionary<string, object?>();

        // An explicit null disables the wildcard, so it is written; an undeclared one is omitted.
        if (meta.AnyAction is not null) data["anyAction"] = Wildcard(meta.AnyAction);
        if (meta.AnySubject is not null) data["anySubject"] = Wildcard(meta.AnySubject);
        if (meta.Actions is not null) data["actions"] = meta.Actions.Cast<object?>().ToList();
        if (meta.Subjects is not null) data["subjects"] = meta.Subjects.Cast<object?>().ToList();
        if (meta.Operators is not null) data["operators"] = meta.Operators.Cast<object?>().ToList();
        if (meta.Application is not null) data["application"] = meta.Application;
        return data;
    }

    private static object? Wildcard(WildcardToken token) => token is WildcardToken.Named named ? named.Token : null;

    private static Rule ReadRule(object? element)
    {
        if (AsList(element) is not { Count: 3 or 4 } tuple)
        {
            throw new PolicyLoadException("Malformed rule tuple: a rule must be [effect, action, subject, condition?].");
        }

        IReadOnlyDictionary<string, object?>? conditions = null;
        if (tuple.Count == 4 && tuple[3] is not null)
        {
            conditions = AsMap(tuple[3])
                ?? throw new PolicyLoadException("Malformed rule tuple: the condition must be an object.");
        }

        return new Rule(Text(tuple[0], "effect"), Text(tuple[1], "action"), Text(tuple[2], "subject"), conditions);
    }

    private static List<object?> WriteRule(Rule rule)
    {
        var tuple = new List<object?> { rule.Effect, rule.Action, rule.Subject };
        if (rule.Conditions is not null) tuple.Add(rule.Conditions);
        return tuple;
    }

    private static PolicyTestSuite ReadSuite(object? element)
    {
        if (AsMap(element) is not { } suite || suite.GetValueOrDefault("name") is not string name
            || AsList(suite.GetValueOrDefault("cases")) is not { } cases)
        {
            throw new PolicyLoadException("Malformed test suite: expected an object with a name and cases.");
        }

        return new PolicyTestSuite
        {
            Name = name,
            Description = suite.GetValueOrDefault("description") as string,
            Cases = cases.Select(ReadCase).ToList(),
        };
    }

    private static PolicyTestCase ReadCase(object? element)
    {
        if (AsMap(element) is not { } testCase
            || AsList(testCase.GetValueOrDefault("check")) is not { Count: 2 or 3 } check
            || testCase.GetValueOrDefault("expected") is not bool expected)
        {
            throw new PolicyLoadException("Malformed test case: expected a check and a boolean expected.");
        }

        return new PolicyTestCase
        {
            Name = testCase.GetValueOrDefault("name") as string,
            Check = new PolicyTestCheck(
                Text(check[0], "test check action"),
                Text(check[1], "test check subject"),
                check.Count == 3 ? check[2] : null),
            Expected = expected,
        };
    }

    private static Dictionary<string, object?> WriteSuite(PolicyTestSuite suite)
    {
        var data = new Dictionary<string, object?> { ["name"] = suite.Name };
        if (suite.Description is not null) data["description"] = suite.Description;
        data["cases"] = suite.Cases.Select(c => (object?)WriteCase(c)).ToList();
        return data;
    }

    private static Dictionary<string, object?> WriteCase(PolicyTestCase testCase)
    {
        var check = new List<object?> { testCase.Check.Action, testCase.Check.Subject };
        if (testCase.Check.Claims is not null) check.Add(testCase.Check.Claims);

        var data = new Dictionary<string, object?>();
        if (testCase.Name is not null) data["name"] = testCase.Name;
        data["check"] = check;
        data["expected"] = testCase.Expected;
        return data;
    }

    private static string Text(object? value, string position) =>
        value as string ?? throw new PolicyLoadException($"Malformed policy document: {position} must be a string.");

    private static List<string>? Strings(IReadOnlyDictionary<string, object?> map, string key)
    {
        if (map.GetValueOrDefault(key) is not { } value) return null;

        var list = AsList(value) ?? throw new PolicyLoadException($"meta.{key} must be an array of strings.");
        return list.Select(item => Text(item, $"a meta.{key} entry")).ToList();
    }

    private static IReadOnlyDictionary<string, object?>? AsMap(object? value)
    {
        switch (value)
        {
            case IReadOnlyDictionary<string, object?> map:
                return map;
            case IDictionary<string, object?> map:
                return new Dictionary<string, object?>(map);
            case IDictionary map:
                return map.Keys.Cast<object>().ToDictionary(k => Convert.ToString(k)!, k => map[k]);
            default:
                return null;
        }
    }

    private static List<object?>? AsList(object? value) =>
        value is IEnumerable and not string and not IDictionary and not IReadOnlyDictionary<string, object?>
            ? ((IEnumerable)value).Cast<object?>().ToList()
            : null;
}
