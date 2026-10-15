using Keycard.Errors;
using Xunit;

namespace Keycard.Tests;

/// <summary>Reads and writes policy documents as plain data through <see cref="PolicyDefinition.FromData"/> and <see cref="PolicyDefinition.ToData"/>.</summary>
public class PolicyDataTests
{
    private const string Document = """
        {
          "version": "0.1.3",
          "name": "Articles",
          "description": "d",
          "meta": {
            "anyAction": "*",
            "anySubject": null,
            "actions": ["Read", "Update"],
            "subjects": ["Article"],
            "operators": ["$hasRole"],
            "application": { "owner": "team-a", "tags": [1, 2.5, "x", null, true] }
          },
          "rules": [
            ["allow", "Read", "Article"],
            ["deny", "Update", "Article", { "status": { "$in": ["archived", "locked"] }, "n": { "$gt": 1.5 } }]
          ],
          "tests": [
            { "name": "s", "cases": [
              { "name": "c1", "check": ["Update", "Article", { "status": "open" }], "expected": true },
              { "check": ["Read", "Article"], "expected": false }
            ] }
          ]
        }
        """;

    [Fact]
    public void RoundTripsADocumentWithoutLosingAnything()
    {
        var definition = PolicyJson.FromJson(Document);

        var again = PolicyJson.FromJson(PolicyJson.ToJson(definition));

        Assert.Equal(PolicyJson.ToJson(definition), PolicyJson.ToJson(again));
        Assert.Equal("0.1.3", again.Version);
        Assert.Equal(new WildcardToken.Named("*"), again.Meta!.AnyAction);
        Assert.Equal(WildcardToken.Off, again.Meta.AnySubject);
        Assert.Equal(2, again.Rules.Count);
        Assert.Equal("c1", again.Tests![0].Cases[0].Name);
        Assert.Null(again.Tests[0].Cases[1].Check.Claims);
    }

    [Fact]
    public void KeepsAnAbsentWildcardDistinctFromAnExplicitNull()
    {
        var absent = PolicyJson.FromJson("""{ "version": "0.1", "meta": {}, "rules": [] }""");
        var disabled = PolicyJson.FromJson("""{ "version": "0.1", "meta": { "anyAction": null }, "rules": [] }""");

        Assert.Null(absent.Meta!.AnyAction);
        Assert.Equal(WildcardToken.Off, disabled.Meta!.AnyAction);
    }

    [Fact]
    public void PreservesTheDocumentsOwnVersionThroughAPolicy()
    {
        var policy = new Policy(PolicyJson.FromJson("""{ "version": "0.0.5", "rules": [] }"""));

        Assert.Equal("0.0.5", policy.Definition.Version);
    }

    [Fact]
    public void ApplicationDataNeverCausesRejection()
    {
        foreach (var application in new[] { "1", "\"x\"", "[1, [2]]", "{ \"a\": { \"b\": null } }", "null" })
        {
            var policy = new Policy(PolicyJson.FromJson(
                $$"""{ "version": "0.1", "meta": { "application": {{application}} }, "rules": [] }"""));

            Assert.NotNull(policy);
        }
    }

    [Fact]
    public void ParsedConditionsEvaluateAgainstNumbersOfAnyClrType()
    {
        var policy = new Policy(PolicyJson.FromJson(
            """{ "version": "0.1", "rules": [["allow", "Read", "Article", { "n": { "$gte": 2 }, "id": 7 }]] }"""));

        Assert.True(policy.Can("Read", Subject.Named("Article", new { n = 2.5, id = 7L })));
        Assert.True(policy.Can("Read", Subject.Named("Article", new { n = 2m, id = (short)7 })));
        Assert.False(policy.Can("Read", Subject.Named("Article", new { n = 1, id = 7 })));
    }

    [Fact]
    public void RequiresAVersionWhenLoadingJson()
    {
        Assert.Throws<PolicyLoadException>(() => PolicyJson.FromJson("""{ "rules": [] }"""));
    }

    [Theory]
    [InlineData("""["allow", "Read"]""")]
    [InlineData("""["allow", 1, "Article"]""")]
    [InlineData("""["allow", "Read", null]""")]
    [InlineData("""["allow", "Read", "Article", "open"]""")]
    [InlineData("""{ "effect": "allow" }""")]
    public void RejectsMalformedRuleTuples(string rule)
    {
        Assert.Throws<PolicyLoadException>(() => PolicyJson.FromJson($$"""{ "version": "0.1", "rules": [{{rule}}] }"""));
    }

    [Fact]
    public void TreatsNullRulesAsAnEmptyPolicy()
    {
        var policy = new Policy(PolicyJson.FromJson("""{ "version": "0.1", "rules": null }"""));

        Assert.False(policy.Can("Read", "Article"));
    }

    // --- plain data ---

    private static Dictionary<string, object?> Doc(params (string Key, object? Value)[] entries) =>
        entries.ToDictionary(e => e.Key, e => e.Value);

    [Fact]
    public void WritesTheDocumentShapeAsPlainData()
    {
        var def = new PolicyBuilder().Allow("Update", "Article", Conditions.Condition.Eq("ownerId", 1)).BuildDef();

        var data = def.ToData();

        Assert.Equal("0.1", data["version"]);
        var rule = (IList<object?>)((IList<object?>)data["rules"]!)[0]!;
        Assert.Equal(new object?[] { "allow", "Update", "Article" }, rule.Take(3));
        Assert.Equal(1, ((IDictionary<string, object?>)((IDictionary<string, object?>)rule[3]!)["ownerId"]!)["$eq"]);
        Assert.Equal(new[] { "Article" }, (IEnumerable<object?>)((IDictionary<string, object?>)data["meta"]!)["subjects"]!);
    }

    [Fact]
    public void ReadsDataWithoutModifyingIt()
    {
        var rule = new List<object?> { "allow", "Read", "Article", new Dictionary<string, object?> { ["n"] = new Dictionary<string, object?> { ["$gt"] = 1 } } };
        var document = Doc(("version", "0.1"), ("rules", new List<object?> { rule }));

        var policy = new Policy(PolicyDefinition.FromData(document));
        ((IDictionary<string, object?>)rule[3]!)["n"] = null;

        Assert.True(policy.Can("Read", Subject.Named("Article", new { n = 2 })));
        Assert.Equal(2, document.Count);
    }

    [Fact]
    public void TheWrittenDataIsIndependentOfTheDefinition()
    {
        var def = new PolicyBuilder().Allow("Read", "Article").BuildDef();
        var data = def.ToData();

        def.Rules.Add(new Rule("allow", "Delete", "Article"));
        ((IList<object?>)data["rules"]!).Clear();

        Assert.Equal(2, def.Rules.Count);
        Assert.Equal(2, ((IList<object?>)def.ToData()["rules"]!).Count);
    }

    [Fact]
    public void AcceptsNumbersOfAnyClrType()
    {
        var condition = new Dictionary<string, object?> { ["a"] = 2, ["b"] = new Dictionary<string, object?> { ["$gte"] = 1.5m } };
        var document = Doc(("version", "0.1"), ("rules", new List<object?> { new List<object?> { "allow", "Read", "Article", condition } }));
        var policy = new Policy(PolicyDefinition.FromData(document));

        Assert.True(policy.Can("Read", Subject.Named("Article", new { a = 2L, b = 1.5 })));
        Assert.False(policy.Can("Read", Subject.Named("Article", new { a = 3, b = 2 })));
    }

    [Fact]
    public void WritesAnExplicitNullWildcardAndOmitsAnUndeclaredOne()
    {
        var def = new PolicyDefinition { Meta = new PolicyMeta { AnySubject = WildcardToken.Off } };

        var meta = (IDictionary<string, object?>)def.ToData()["meta"]!;

        Assert.True(meta.ContainsKey("anySubject"));
        Assert.Null(meta["anySubject"]);
        Assert.False(meta.ContainsKey("anyAction"));
    }

    [Fact]
    public void ReadsMapsAndListsOfAnyCommonCollectionType()
    {
        var document = new System.Collections.Hashtable
        {
            ["version"] = "0.1",
            ["rules"] = new object?[] { new object?[] { "allow", "Read", "Article" } },
        };

        Assert.Single(PolicyDefinition.FromData(document).Rules);
    }

    [Theory]
    [InlineData("""{ "rules": [] }""")]
    [InlineData("""{ "version": 1, "rules": [] }""")]
    [InlineData("""{ "version": "0.1", "rules": {} }""")]
    [InlineData("""{ "version": "0.1", "rules": "x" }""")]
    [InlineData("""{ "version": "0.1", "meta": [] , "rules": [] }""")]
    [InlineData("""{ "version": "0.1", "meta": { "actions": "Read" }, "rules": [] }""")]
    [InlineData("""{ "version": "0.1", "meta": { "actions": [1] }, "rules": [] }""")]
    [InlineData("""{ "version": "0.1", "rules": [], "tests": [{ "name": "s" }] }""")]
    [InlineData("""{ "version": "0.1", "rules": [], "tests": [{ "name": "s", "cases": [{ "check": ["A"], "expected": true }] }] }""")]
    public void RejectsMalformedDocuments(string json)
    {
        Assert.Throws<PolicyLoadException>(() => PolicyJson.FromJson(json));
    }

    [Theory]
    [InlineData(null)]
    [InlineData("a string")]
    public void RejectsADocumentThatIsNotAMap(object? document)
    {
        Assert.Throws<PolicyLoadException>(() => PolicyDefinition.FromData(document));
    }
}
