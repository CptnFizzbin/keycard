using System.Text.Json;
using System.Text.Json.Nodes;
using Keycard.Errors;

namespace Keycard.Tests;

/// <summary>
/// What a host application adds around <see cref="PolicyDefinition.FromJsonObject"/>
/// and <see cref="PolicyDefinition.ToJsonObject"/>: Keycard converts to and from
/// <see cref="JsonObject"/>, and the application owns turning that into text.
/// </summary>
internal static class PolicyJson
{
    public static PolicyDefinition FromJson(string json)
    {
        try
        {
            return PolicyDefinition.FromJsonObject(
                JsonNode.Parse(json) as JsonObject ?? throw new PolicyLoadException("A policy document must be an object."));
        }
        catch (JsonException e)
        {
            throw new PolicyLoadException($"Invalid policy document: {e.Message}", e);
        }
    }

    public static string ToJson(PolicyDefinition definition) => definition.ToJsonObject().ToJsonString();

    /// <summary>Reads a document held as plain dictionaries and lists, the shape a YAML parser produces.</summary>
    public static PolicyDefinition FromPlain(object document) =>
        PolicyDefinition.FromJsonObject((JsonObject)JsonSerializer.SerializeToNode(document)!);
}
