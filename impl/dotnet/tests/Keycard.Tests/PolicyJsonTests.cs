using Keycard.Errors;
using Xunit;

namespace Keycard.Tests;

/// <summary>Exercises <see cref="PolicyJson"/>, the sample mapper a host application would write.</summary>
public class PolicyJsonTests
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
    public void ReportsMalformedJsonAsALoadError()
    {
        Assert.Throws<PolicyLoadException>(() => PolicyJson.FromJson("{ not json"));
        Assert.Throws<PolicyLoadException>(() => PolicyJson.FromJson("null"));
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
}
