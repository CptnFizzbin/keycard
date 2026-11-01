using System.Collections;
using System.Globalization;
using System.Text.Json;
using System.Text.Json.Nodes;
using Keycard.Errors;

namespace Keycard.Json;

/// <summary>Reads and writes <see cref="PolicyDefinition"/> documents as JSON.</summary>
/// <example>
/// <code>
/// var definition = PolicyJson.Parse(text);
/// string text = PolicyJson.Serialize(definition);
/// JsonObject node = PolicyJson.Write(definition);
/// </code>
/// </example>
public static class PolicyJson
{
    /// <summary>Parses a JSON policy document.</summary>
    /// <exception cref="PolicyLoadException">The text isn't valid JSON, or isn't a valid policy document.</exception>
    public static PolicyDefinition Parse(string json)
    {
        ArgumentNullException.ThrowIfNull(json);

        JsonNode? node;
        try
        {
            node = JsonNode.Parse(json);
        }
        catch (Exception e) when (e is JsonException or ArgumentException)
        {
            throw new PolicyLoadException($"Invalid policy document: {e.Message}", e);
        }

        return Read(node as JsonObject ?? throw new PolicyLoadException("A policy document must be a JSON object."));
    }

    /// <summary>Reads a definition from a <see cref="JsonObject"/>, which is not modified.</summary>
    /// <exception cref="PolicyLoadException">The document is malformed.</exception>
    public static PolicyDefinition Read(JsonObject document)
    {
        ArgumentNullException.ThrowIfNull(document);
        return PolicyDefinition.FromData(ToData(document));
    }

    /// <summary>Returns <paramref name="definition"/> as a new <see cref="JsonObject"/>.</summary>
    public static JsonObject Write(PolicyDefinition definition)
    {
        ArgumentNullException.ThrowIfNull(definition);
        return (JsonObject)ToNode(definition.ToData())!;
    }

    /// <summary>Writes <paramref name="definition"/> as JSON text.</summary>
    public static string Serialize(PolicyDefinition definition, bool indented = false) =>
        Write(definition).ToJsonString(new JsonSerializerOptions { WriteIndented = indented });

    // Plain data (maps, lists, strings, longs, doubles, bools, null) is what PolicyDefinition
    // reads, so a JsonNode is copied out of rather than kept.
    internal static object? ToData(JsonNode? node)
    {
        switch (node)
        {
            case null:
                return null;
            case JsonObject obj:
                return obj.ToDictionary(kv => kv.Key, kv => ToData(kv.Value));
            case JsonArray array:
                return array.Select(ToData).ToList();
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

    internal static JsonNode? ToNode(object? value)
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
                foreach (DictionaryEntry entry in map) obj[Convert.ToString(entry.Key)!] = ToNode(entry.Value);
                return obj;
            }
            case IReadOnlyDictionary<string, object?> map:
            {
                var obj = new JsonObject();
                foreach (var (key, item) in map) obj[key] = ToNode(item);
                return obj;
            }
            case IEnumerable sequence:
            {
                var array = new JsonArray();
                foreach (var item in sequence) array.Add(ToNode(item));
                return array;
            }
            default:
                return JsonSerializer.SerializeToNode(value, value.GetType());
        }
    }
}
