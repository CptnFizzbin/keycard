using System.Collections;
using System.Text.Json;
using System.Text.Json.Serialization;

namespace Keycard;

/// <summary>
/// Reads any JSON value as plain CLR data - maps as <c>Dictionary&lt;string, object?&gt;</c>,
/// arrays as <c>List&lt;object?&gt;</c>, integers as <c>long</c>, other numbers
/// as <c>double</c> - so conditions and claims work the same whether they came
/// from a document or from code.
/// </summary>
internal sealed class PlainJsonConverter : JsonConverter<object?>
{
    public override bool HandleNull => true;

    public override object? Read(ref Utf8JsonReader reader, Type typeToConvert, JsonSerializerOptions options) =>
        ReadValue(ref reader);

    public override void Write(Utf8JsonWriter writer, object? value, JsonSerializerOptions options) =>
        WriteValue(writer, value);

    public static object? ReadValue(ref Utf8JsonReader reader)
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

    public static void WriteValue(Utf8JsonWriter writer, object? value)
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
