using System.Text.Json;
using System.Text.Json.Nodes;
using Keycard.Errors;

namespace Keycard.Tests;

/// <summary>
/// A minimal JSON reader and writer for these tests, so they can state policy
/// documents as JSON text without depending on a format package. It reads JSON
/// into plain data and hands that to <see cref="PolicyDefinition.FromData"/>.
/// </summary>
internal static class PolicyJson
{
    public static PolicyDefinition FromJson(string json)
    {
        try
        {
            return PolicyDefinition.FromData(PlainTree.FromNode(JsonNode.Parse(json)));
        }
        catch (JsonException e)
        {
            throw new PolicyLoadException($"Invalid policy document: {e.Message}", e);
        }
    }

    public static string ToJson(PolicyDefinition definition) => JsonSerializer.Serialize(definition.ToData());
}
