using System.Collections;
using System.Text.Json;
using Keycard.Errors;

namespace Keycard.Tests;

/// <summary>
/// A sample of what a host application writes to read and write policy
/// documents: Keycard itself does no serialization, so the application maps
/// <see cref="PolicyDefinition"/> to and from the format it chooses. This one
/// uses JSON via a plain tree of dictionaries, lists, and primitives, which is
/// also the shape a YAML parser produces.
/// </summary>
internal static class PolicyJson
{
    public static PolicyDefinition FromJson(string json)
    {
        try
        {
            var reader = new Utf8JsonReader(System.Text.Encoding.UTF8.GetBytes(json));
            reader.Read();
            return FromPlain(ReadValue(ref reader));
        }
        catch (JsonException e)
        {
            throw new PolicyLoadException($"Invalid policy document: {e.Message}", e);
        }
    }

    public static string ToJson(PolicyDefinition definition)
    {
        using var stream = new MemoryStream();
        using (var writer = new Utf8JsonWriter(stream))
        {
            WriteValue(writer, ToPlain(definition));
        }

        return System.Text.Encoding.UTF8.GetString(stream.ToArray());
    }

    public static PolicyDefinition FromPlain(object? document)
    {
        if (document is not Dictionary<string, object?> doc)
        {
            throw new PolicyLoadException("A policy document must be an object.");
        }

        var definition = new PolicyDefinition
        {
            Version = doc.GetValueOrDefault("version") as string
                ?? throw new PolicyLoadException("A policy document must declare a string version."),
            Name = doc.GetValueOrDefault("name") as string,
            Description = doc.GetValueOrDefault("description") as string,
            Rules = (doc.GetValueOrDefault("rules") as IEnumerable<object?> ?? Array.Empty<object?>()).Select(ReadRule).ToList(),
        };

        if (doc.GetValueOrDefault("meta") is Dictionary<string, object?> meta) definition.Meta = ReadMeta(meta);
        if (doc.GetValueOrDefault("tests") is IEnumerable<object?> tests) definition.Tests = tests.Select(ReadSuite).ToList();
        return definition;
    }

    public static IReadOnlyDictionary<string, object?> ToPlain(PolicyDefinition definition)
    {
        var doc = new Dictionary<string, object?> { ["version"] = definition.Version };
        if (definition.Name is not null) doc["name"] = definition.Name;
        if (definition.Description is not null) doc["description"] = definition.Description;

        if (definition.Meta is { } meta)
        {
            var m = new Dictionary<string, object?>();
            // An explicit null disables the wildcard, so it is written; an undeclared one is omitted.
            if (meta.AnyAction is not null) m["anyAction"] = Wildcard(meta.AnyAction);
            if (meta.AnySubject is not null) m["anySubject"] = Wildcard(meta.AnySubject);
            if (meta.Actions is not null) m["actions"] = meta.Actions;
            if (meta.Subjects is not null) m["subjects"] = meta.Subjects;
            if (meta.Operators is not null) m["operators"] = meta.Operators;
            if (meta.Application is not null) m["application"] = meta.Application;
            doc["meta"] = m;
        }

        doc["rules"] = definition.Rules.Select(r =>
        {
            var tuple = new List<object?> { r.Effect, r.Action, r.Subject };
            if (r.Conditions is not null) tuple.Add(r.Conditions);
            return tuple;
        }).ToList();

        if (definition.Tests is not null)
        {
            doc["tests"] = definition.Tests.Select(suite => new Dictionary<string, object?>
            {
                ["name"] = suite.Name,
                ["cases"] = suite.Cases.Select(c => new Dictionary<string, object?>
                {
                    ["name"] = c.Name,
                    ["check"] = c.Check.Claims is null
                        ? new List<object?> { c.Check.Action, c.Check.Subject }
                        : new List<object?> { c.Check.Action, c.Check.Subject, c.Check.Claims },
                    ["expected"] = c.Expected,
                }).ToList(),
            }).ToList();
        }

        return doc;
    }

    private static object? Wildcard(WildcardToken token) => token is WildcardToken.Named n ? n.Token : null;

    private static PolicyMeta ReadMeta(Dictionary<string, object?> meta) => new()
    {
        // WildcardToken.Of reads an explicit null as "disabled", so only a key that is
        // present is converted; an absent one stays undeclared.
        AnyAction = meta.TryGetValue("anyAction", out var anyAction) ? WildcardToken.Of(anyAction) : null,
        AnySubject = meta.TryGetValue("anySubject", out var anySubject) ? WildcardToken.Of(anySubject) : null,
        Actions = Strings(meta.GetValueOrDefault("actions")),
        Subjects = Strings(meta.GetValueOrDefault("subjects")),
        Operators = Strings(meta.GetValueOrDefault("operators")),
        Application = meta.GetValueOrDefault("application"),
    };

    private static List<string>? Strings(object? value) => (value as IEnumerable<object?>)?.Select(v => (string)v!).ToList();

    private static Rule ReadRule(object? element)
    {
        if (element is not List<object?> { Count: 3 or 4 } tuple)
        {
            throw new PolicyLoadException("Malformed rule tuple: a rule must be [effect, action, subject, condition?].");
        }

        var effect = Text(tuple[0], "effect");
        var action = Text(tuple[1], "action");
        var subject = Text(tuple[2], "subject");

        IReadOnlyDictionary<string, object?>? conditions = null;
        if (tuple.Count == 4 && tuple[3] is not null)
        {
            conditions = tuple[3] as Dictionary<string, object?>
                ?? throw new PolicyLoadException("Malformed rule tuple: the condition must be an object.");
        }

        return new Rule(effect, action, subject, conditions);
    }

    private static string Text(object? value, string position) =>
        value as string ?? throw new PolicyLoadException($"Malformed rule tuple: {position} must be a string.");

    private static PolicyTestSuite ReadSuite(object? element)
    {
        var suite = (Dictionary<string, object?>)element!;
        return new PolicyTestSuite
        {
            Name = (string)suite["name"]!,
            Description = suite.GetValueOrDefault("description") as string,
            Cases = ((IEnumerable<object?>)suite["cases"]!).Select(c =>
            {
                var testCase = (Dictionary<string, object?>)c!;
                var check = (List<object?>)testCase["check"]!;
                return new PolicyTestCase
                {
                    Name = testCase.GetValueOrDefault("name") as string,
                    Check = new PolicyTestCheck((string)check[0]!, (string)check[1]!, check.Count == 3 ? check[2] : null),
                    Expected = (bool)testCase["expected"]!,
                };
            }).ToList(),
        };
    }

    private static object? ReadValue(ref Utf8JsonReader reader)
    {
        switch (reader.TokenType)
        {
            case JsonTokenType.Null:
                return null;
            case JsonTokenType.True:
                return true;
            case JsonTokenType.False:
                return false;
            case JsonTokenType.String:
                return reader.GetString();
            case JsonTokenType.Number:
                return reader.TryGetInt64(out var integer) ? integer : reader.GetDouble();
            case JsonTokenType.StartArray:
            {
                var list = new List<object?>();
                while (reader.Read() && reader.TokenType != JsonTokenType.EndArray) list.Add(ReadValue(ref reader));
                return list;
            }
            case JsonTokenType.StartObject:
            {
                var map = new Dictionary<string, object?>();
                while (reader.Read() && reader.TokenType != JsonTokenType.EndObject)
                {
                    var key = reader.GetString()!;
                    reader.Read();
                    map[key] = ReadValue(ref reader);
                }

                return map;
            }
            default:
                throw new JsonException($"Unexpected token {reader.TokenType}.");
        }
    }

    private static void WriteValue(Utf8JsonWriter writer, object? value)
    {
        switch (value)
        {
            case null:
                writer.WriteNullValue();
                break;
            case string s:
                writer.WriteStringValue(s);
                break;
            case bool b:
                writer.WriteBooleanValue(b);
                break;
            case IDictionary map:
                writer.WriteStartObject();
                foreach (DictionaryEntry entry in map)
                {
                    writer.WritePropertyName(entry.Key.ToString()!);
                    WriteValue(writer, entry.Value);
                }

                writer.WriteEndObject();
                break;
            case IReadOnlyDictionary<string, object?> map:
                writer.WriteStartObject();
                foreach (var (key, item) in map)
                {
                    writer.WritePropertyName(key);
                    WriteValue(writer, item);
                }

                writer.WriteEndObject();
                break;
            case IEnumerable sequence:
                writer.WriteStartArray();
                foreach (var item in sequence) WriteValue(writer, item);
                writer.WriteEndArray();
                break;
            default:
                JsonSerializer.Serialize(writer, value, value.GetType());
                break;
        }
    }
}
