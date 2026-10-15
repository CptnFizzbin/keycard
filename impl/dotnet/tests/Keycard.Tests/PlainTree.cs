using System.Globalization;
using System.Text.Json;
using System.Text.Json.Nodes;

namespace Keycard.Tests;

/// <summary>
/// Converts a <see cref="JsonNode"/> to plain data (dictionaries, lists,
/// strings, longs, doubles, bools, null): the common shape a YAML codec
/// reads and writes, and what <see cref="PolicyDefinition.FromData"/> turns back
/// into a definition.
/// </summary>
internal static class PlainTree
{
    public static object? FromNode(JsonNode? node)
    {
        switch (node)
        {
            case null:
                return null;
            case JsonObject obj:
                return obj.ToDictionary(kv => kv.Key, kv => FromNode(kv.Value));
            case JsonArray array:
                return array.Select(FromNode).ToList();
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
}
