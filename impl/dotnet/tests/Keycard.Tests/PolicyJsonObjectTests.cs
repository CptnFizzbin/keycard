using System.Text.Json.Nodes;
using Keycard.Errors;
using Xunit;

namespace Keycard.Tests;

public class PolicyJsonObjectTests
{
    private static JsonObject Parse(string json) => (JsonObject)JsonNode.Parse(json)!;

    [Fact]
    public void WritesTheDocumentShapeAsAJsonObject()
    {
        var def = new PolicyBuilder().Allow("Update", "Article", Conditions.Condition.Eq("ownerId", 1)).BuildDef();

        var node = def.ToJsonObject();

        Assert.Equal("0.1", (string?)node["version"]);
        Assert.Equal("Update", (string?)node["rules"]![0]![1]);
        Assert.Equal(1, (int?)node["rules"]![0]![3]!["ownerId"]!["$eq"]);
        Assert.Equal("Article", (string?)node["meta"]!["subjects"]![0]);
    }

    [Fact]
    public void ReadsAJsonObjectWithoutModifyingIt()
    {
        var document = Parse("""{ "version": "0.1", "rules": [["allow", "Read", "Article", { "n": { "$gt": 1 } }]] }""");
        var before = document.ToJsonString();

        var policy = new Policy(PolicyDefinition.FromJsonObject(document));

        Assert.Equal(before, document.ToJsonString());
        Assert.True(policy.Can("Read", Subject.Named("Article", new { n = 2 })));
    }

    [Fact]
    public void TheWrittenObjectIsIndependentOfTheDefinition()
    {
        var def = new PolicyBuilder().Allow("Read", "Article").BuildDef();
        var node = def.ToJsonObject();

        def.Rules.Add(new Rule("allow", "Delete", "Article"));
        node["rules"]!.AsArray().Clear();

        Assert.Equal(2, def.Rules.Count);
        Assert.Equal(2, def.ToJsonObject()["rules"]!.AsArray().Count);
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
        var policy = new Policy(PolicyDefinition.FromJsonObject(document));

        Assert.True(policy.Can("Read", Subject.Named("Article", new { a = 2L, b = 1.5 })));
        Assert.False(policy.Can("Read", Subject.Named("Article", new { a = 3, b = 2 })));
    }

    [Fact]
    public void WritesAnExplicitNullWildcardAndOmitsAnUndeclaredOne()
    {
        var def = new PolicyDefinition { Meta = new PolicyMeta { AnySubject = WildcardToken.Off } };

        var meta = def.ToJsonObject()["meta"]!.AsObject();

        Assert.True(meta.ContainsKey("anySubject"));
        Assert.Null(meta["anySubject"]);
        Assert.False(meta.ContainsKey("anyAction"));
    }

    [Theory]
    [InlineData("""{ "rules": [] }""")]
    [InlineData("""{ "version": 1, "rules": [] }""")]
    [InlineData("""{ "version": "0.1", "rules": {} }""")]
    [InlineData("""{ "version": "0.1", "meta": { "actions": "Read" }, "rules": [] }""")]
    [InlineData("""{ "version": "0.1", "meta": { "actions": [1] }, "rules": [] }""")]
    [InlineData("""{ "version": "0.1", "rules": [], "tests": [{ "name": "s" }] }""")]
    [InlineData("""{ "version": "0.1", "rules": [], "tests": [{ "name": "s", "cases": [{ "check": ["A"], "expected": true }] }] }""")]
    public void RejectsMalformedDocuments(string json)
    {
        Assert.Throws<PolicyLoadException>(() => PolicyDefinition.FromJsonObject(Parse(json)));
    }

    [Fact]
    public void RejectsANullDocument()
    {
        Assert.Throws<ArgumentNullException>(() => PolicyDefinition.FromJsonObject(null!));
    }
}
