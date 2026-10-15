using System.Text.Json;
using System.Text.Json.Nodes;
using System.Text.Json.Serialization;
using Keycard.Errors;

namespace Keycard.Json;

/// <summary>
/// Lets <see cref="JsonSerializer"/> read and write a <see cref="PolicyDefinition"/>
/// as a policy document, for a definition nested inside the application's own types.
/// </summary>
/// <example>
/// <code>
/// var options = new JsonSerializerOptions { Converters = { new PolicyDefinitionJsonConverter() } };
/// var definition = JsonSerializer.Deserialize&lt;PolicyDefinition&gt;(text, options);
/// </code>
/// </example>
public sealed class PolicyDefinitionJsonConverter : JsonConverter<PolicyDefinition>
{
    public override PolicyDefinition Read(ref Utf8JsonReader reader, Type typeToConvert, JsonSerializerOptions options)
    {
        var node = JsonNode.Parse(ref reader) as JsonObject
            ?? throw new PolicyLoadException("A policy document must be a JSON object.");
        return PolicyJson.Read(node);
    }

    public override void Write(Utf8JsonWriter writer, PolicyDefinition value, JsonSerializerOptions options) =>
        PolicyJson.Write(value).WriteTo(writer);
}
