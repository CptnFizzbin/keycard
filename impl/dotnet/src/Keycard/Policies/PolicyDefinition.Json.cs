using System.Collections;
using System.Globalization;
using System.Text.Json;
using System.Text.Json.Nodes;
using Keycard.Errors;

namespace Keycard;

public sealed partial class PolicyDefinition
{
    /// <summary>
    /// Reads a definition from a <see cref="JsonObject"/> in the policy document
    /// shape. Parsing text into a <see cref="JsonObject"/> is the application's
    /// job; <paramref name="document"/> is not modified or retained.
    /// </summary>
    /// <example>
    /// <code>
    /// var definition = PolicyDefinition.FromJsonObject((JsonObject)JsonNode.Parse(text)!);
    /// </code>
    /// </example>
    /// <exception cref="PolicyLoadException">The document is malformed.</exception>
    public static PolicyDefinition FromJsonObject(JsonObject document)
    {
        ArgumentNullException.ThrowIfNull(document);

        var definition = new PolicyDefinition
        {
            Version = Optional(document, "version") as string
                ?? throw new PolicyLoadException("A policy document must declare a string version."),
            Name = Optional(document, "name") as string,
            Description = Optional(document, "description") as string,
        };

        if (document["rules"] is JsonArray rules)
        {
            definition.Rules = rules.Select(ReadRule).ToList();
        }
        else if (document["rules"] is not null)
        {
            throw new PolicyLoadException("A policy's rules must be an array.");
        }

        if (document["meta"] is JsonObject meta) definition.Meta = ReadMeta(meta);
        if (document["tests"] is JsonArray tests) definition.Tests = tests.Select(ReadSuite).ToList();
        return definition;
    }

    /// <summary>
    /// Returns this definition as a new <see cref="JsonObject"/> in the policy
    /// document shape, leaving writing it out as text to the application.
    /// </summary>
    public JsonObject ToJsonObject()
    {
        var document = new JsonObject { ["version"] = Version };
        if (Name is not null) document["name"] = Name;
        if (Description is not null) document["description"] = Description;
        if (Meta is not null) document["meta"] = WriteMeta(Meta);

        document["rules"] = new JsonArray(Rules.Select(WriteRule).ToArray());

        if (Tests is not null) document["tests"] = new JsonArray(Tests.Select(WriteSuite).ToArray());
        return document;
    }

    private static PolicyMeta ReadMeta(JsonObject meta) => new()
    {
        // WildcardToken.Of reads an explicit null as "disabled", so only a key that is
        // present is converted; an absent one stays undeclared.
        AnyAction = meta.ContainsKey("anyAction") ? WildcardToken.Of(Plain(meta["anyAction"])) : null,
        AnySubject = meta.ContainsKey("anySubject") ? WildcardToken.Of(Plain(meta["anySubject"])) : null,
        Actions = Strings(meta, "actions"),
        Subjects = Strings(meta, "subjects"),
        Operators = Strings(meta, "operators"),
        Application = Plain(meta["application"]),
    };

    private static JsonObject WriteMeta(PolicyMeta meta)
    {
        var node = new JsonObject();

        // An explicit null disables the wildcard, so it is written; an undeclared one is omitted.
        if (meta.AnyAction is not null) node["anyAction"] = Wildcard(meta.AnyAction);
        if (meta.AnySubject is not null) node["anySubject"] = Wildcard(meta.AnySubject);
        if (meta.Actions is not null) node["actions"] = StringArray(meta.Actions);
        if (meta.Subjects is not null) node["subjects"] = StringArray(meta.Subjects);
        if (meta.Operators is not null) node["operators"] = StringArray(meta.Operators);
        if (meta.Application is not null) node["application"] = Node(meta.Application);
        return node;
    }

    private static JsonNode? Wildcard(WildcardToken token) => token is WildcardToken.Named named ? named.Token : null;

    private static Rule ReadRule(JsonNode? element)
    {
        if (element is not JsonArray { Count: 3 or 4 } tuple)
        {
            throw new PolicyLoadException("Malformed rule tuple: a rule must be [effect, action, subject, condition?].");
        }

        IReadOnlyDictionary<string, object?>? conditions = null;
        if (tuple.Count == 4 && tuple[3] is not null)
        {
            conditions = tuple[3] is JsonObject condition
                ? (Dictionary<string, object?>)Plain(condition)!
                : throw new PolicyLoadException("Malformed rule tuple: the condition must be an object.");
        }

        return new Rule(Text(tuple[0], "effect"), Text(tuple[1], "action"), Text(tuple[2], "subject"), conditions);
    }

    private static JsonNode WriteRule(Rule rule)
    {
        var tuple = new JsonArray(rule.Effect, rule.Action, rule.Subject);
        if (rule.Conditions is not null) tuple.Add(Node(rule.Conditions));
        return tuple;
    }

    private static PolicyTestSuite ReadSuite(JsonNode? element)
    {
        if (element is not JsonObject suite || suite["name"] is not JsonValue || suite["cases"] is not JsonArray cases)
        {
            throw new PolicyLoadException("Malformed test suite: expected an object with a name and cases.");
        }

        return new PolicyTestSuite
        {
            Name = Text(suite["name"], "test suite name"),
            Description = Optional(suite, "description") as string,
            Cases = cases.Select(ReadCase).ToList(),
        };
    }

    private static PolicyTestCase ReadCase(JsonNode? element)
    {
        if (element is not JsonObject testCase
            || testCase["check"] is not JsonArray { Count: 2 or 3 } check
            || Plain(testCase["expected"]) is not bool expected)
        {
            throw new PolicyLoadException("Malformed test case: expected a check and a boolean expected.");
        }

        return new PolicyTestCase
        {
            Name = Optional(testCase, "name") as string,
            Check = new PolicyTestCheck(
                Text(check[0], "test check action"),
                Text(check[1], "test check subject"),
                check.Count == 3 ? Plain(check[2]) : null),
            Expected = expected,
        };
    }

    private static JsonNode WriteSuite(PolicyTestSuite suite)
    {
        var node = new JsonObject { ["name"] = suite.Name };
        if (suite.Description is not null) node["description"] = suite.Description;
        node["cases"] = new JsonArray(suite.Cases.Select(WriteCase).ToArray());
        return node;
    }

    private static JsonNode WriteCase(PolicyTestCase testCase)
    {
        var check = new JsonArray(testCase.Check.Action, testCase.Check.Subject);
        if (testCase.Check.Claims is not null) check.Add(Node(testCase.Check.Claims));

        var node = new JsonObject();
        if (testCase.Name is not null) node["name"] = testCase.Name;
        node["check"] = check;
        node["expected"] = testCase.Expected;
        return node;
    }

    private static object? Optional(JsonObject node, string key) => Plain(node[key]);

    private static string Text(JsonNode? node, string position) =>
        Plain(node) as string ?? throw new PolicyLoadException($"Malformed policy document: {position} must be a string.");

    private static List<string>? Strings(JsonObject node, string key)
    {
        if (node[key] is null) return null;
        if (node[key] is not JsonArray array) throw new PolicyLoadException($"meta.{key} must be an array of strings.");
        return array.Select(item => Text(item, $"a meta.{key} entry")).ToList();
    }

    private static JsonArray StringArray(IEnumerable<string> values) => new(values.Select(v => (JsonNode?)v).ToArray());

    // Plain data (maps, lists, strings, longs, doubles, bools, null) is what rules and
    // claims work with, so a JsonNode is copied out of rather than kept.
    private static object? Plain(JsonNode? node)
    {
        switch (node)
        {
            case null:
                return null;
            case JsonObject obj:
                return obj.ToDictionary(kv => kv.Key, kv => Plain(kv.Value));
            case JsonArray array:
                return array.Select(Plain).ToList();
            case JsonValue value:
                switch (value.GetValueKind())
                {
                    case JsonValueKind.String:
                        return value.GetValue<string>();
                    case JsonValueKind.True:
                        return true;
                    case JsonValueKind.False:
                        return false;
                    case JsonValueKind.Number:
                        // Read from the text: a node built from an int or decimal can't be
                        // read back as a long or double directly.
                        var text = value.ToJsonString();
                        return long.TryParse(text, NumberStyles.AllowLeadingSign, CultureInfo.InvariantCulture, out var integer)
                            ? integer
                            : double.Parse(text, NumberStyles.Float, CultureInfo.InvariantCulture);
                    default:
                        return null;
                }
            default:
                return null;
        }
    }

    private static JsonNode? Node(object? value)
    {
        switch (value)
        {
            case null:
                return null;
            case string s:
                return s;
            case bool b:
                return b;
            case IDictionary map:
            {
                var obj = new JsonObject();
                foreach (DictionaryEntry entry in map) obj[Convert.ToString(entry.Key)!] = Node(entry.Value);
                return obj;
            }
            case IReadOnlyDictionary<string, object?> map:
            {
                var obj = new JsonObject();
                foreach (var (key, item) in map) obj[key] = Node(item);
                return obj;
            }
            case IEnumerable sequence:
            {
                var array = new JsonArray();
                foreach (var item in sequence) array.Add(Node(item));
                return array;
            }
            default:
                return JsonSerializer.SerializeToNode(value, value.GetType());
        }
    }
}
