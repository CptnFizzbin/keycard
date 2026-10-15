using System.Text.Json;
using System.Text.Json.Nodes;
using Keycard.Errors;
using Keycard.Json;
using Xunit;

namespace Keycard.Formats.Tests;

public class JsonTests
{
    private sealed record Envelope(string Owner, PolicyDefinition Policy);

    private static readonly JsonSerializerOptions ConverterOptions = new()
    {
        Converters = { new PolicyDefinitionJsonConverter() },
        PropertyNamingPolicy = JsonNamingPolicy.CamelCase,
    };

    [Fact]
    public void WritesTheDocumentShape()
    {
        var def = new PolicyBuilder().Allow("Update", "Article", Conditions.Condition.Eq("ownerId", 1)).BuildDef();

        Assert.Equal(
            """{"version":"0.1","meta":{"actions":["Update"],"subjects":["Article"],"operators":[]},"rules":[["allow","Update","Article",{"ownerId":{"$eq":1}}]]}""",
            PolicyJson.Serialize(def));
    }

    [Fact]
    public void ReadsAJsonObjectWithoutModifyingIt()
    {
        var document = (JsonObject)JsonNode.Parse("""{ "version": "0.1", "rules": [["allow", "Read", "Article", { "n": { "$gt": 1 } }]] }""")!;
        var before = document.ToJsonString();

        var policy = new Policy(PolicyJson.Read(document));

        Assert.Equal(before, document.ToJsonString());
        Assert.True(policy.Can("Read", Subject.Named("Article", new { n = 2 })));
    }

    [Fact]
    public void TheWrittenObjectIsIndependentOfTheDefinition()
    {
        var def = new PolicyBuilder().Allow("Read", "Article").BuildDef();
        var node = PolicyJson.Write(def);

        def.Rules.Add(new Rule("allow", "Delete", "Article"));
        node["rules"]!.AsArray().Clear();

        Assert.Equal(2, PolicyJson.Write(def)["rules"]!.AsArray().Count);
    }

    [Fact]
    public void ReadsNumbersFromNodesBuiltInCode()
    {
        var document = new JsonObject
        {
            ["version"] = "0.1",
            ["rules"] = new JsonArray(new JsonArray("allow", "Read", "Article", new JsonObject
            {
                ["a"] = 2,
                ["b"] = new JsonObject { ["$gte"] = 1.5m },
            })),
        };
        var policy = new Policy(PolicyJson.Read(document));

        Assert.True(policy.Can("Read", Subject.Named("Article", new { a = 2L, b = 1.5 })));
        Assert.False(policy.Can("Read", Subject.Named("Article", new { a = 3, b = 2 })));
    }

    [Theory]
    [InlineData("{ not json")]
    [InlineData("null")]
    [InlineData("[]")]
    [InlineData("\"text\"")]
    [InlineData("""{ "rules": [] }""")]
    public void ReportsABadDocumentAsALoadError(string json)
    {
        Assert.Throws<PolicyLoadException>(() => PolicyJson.Parse(json));
    }

    [Fact]
    public void TheConverterReadsAndWritesAPolicyNestedInAnotherType()
    {
        var original = new Envelope("team", new PolicyBuilder().Allow("Read", "Article").BuildDef());

        var text = JsonSerializer.Serialize(original, ConverterOptions);
        var restored = JsonSerializer.Deserialize<Envelope>(text, ConverterOptions)!;

        Assert.Contains("\"policy\":{\"version\":\"0.1\"", text);
        Assert.Equal("team", restored.Owner);
        Assert.True(new Policy(restored.Policy).Can("Read", "Article"));
    }

    [Fact]
    public void TheConverterReportsABadDocumentAsALoadError()
    {
        Assert.Throws<PolicyLoadException>(() =>
            JsonSerializer.Deserialize<PolicyDefinition>("""{ "rules": [] }""", ConverterOptions));
        Assert.Throws<PolicyLoadException>(() =>
            JsonSerializer.Deserialize<PolicyDefinition>("[]", ConverterOptions));
    }
}
