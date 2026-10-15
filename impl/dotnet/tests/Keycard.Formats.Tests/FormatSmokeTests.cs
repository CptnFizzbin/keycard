using System.Text.Json.Nodes;
using Keycard.Conditions;
using Keycard.Json;
using Keycard.Xml;
using Keycard.Yaml;
using Xunit;

namespace Keycard.Formats.Tests;

/// <summary>Smoke tests that a policy document survives every format package.</summary>
public class FormatSmokeTests
{
    public sealed record Format(string Name, Func<PolicyDefinition, string> Write, Func<string, PolicyDefinition> Read)
    {
        public override string ToString() => Name;
    }

    public static IEnumerable<object[]> Formats()
    {
        yield return new object[] { new Format("JSON", d => PolicyJson.Serialize(d), PolicyJson.Parse) };
        yield return new object[] { new Format("JSON (indented)", d => PolicyJson.Serialize(d, indented: true), PolicyJson.Parse) };
        yield return new object[] { new Format("XML", d => PolicyXml.Serialize(d), PolicyXml.Parse) };
        yield return new object[] { new Format("XML (indented)", d => PolicyXml.Serialize(d, indented: true), PolicyXml.Parse) };
        yield return new object[] { new Format("YAML", PolicyYaml.Serialize, PolicyYaml.Parse) };
    }

    private sealed record ArticleClaims(int OwnerId, string Status, double Score, string? Title);

    private static KeycardConfig Config(Func<object?, object?, bool>? hasRole = null) =>
        new KeycardConfig { Operators = new OperatorCatalog().Add("$hasRole", hasRole ?? ((_, _) => true)) }
            .UseAnyAction("*")
            .DisableAnySubject();

    private static PolicyDefinition RichDefinition()
    {
        var article = new Subject<ArticleClaims>("Article");
        var def = new PolicyBuilder(Config())
            .Allow("Read", article)
            .Allow("Update", article, Condition<ArticleClaims>.Or(
                Condition<ArticleClaims>.Eq(c => c.OwnerId, 1),
                Condition<ArticleClaims>.And(
                    Condition<ArticleClaims>.In(c => c.Status, "draft", "published", "1", "true"),
                    Condition<ArticleClaims>.Gte(c => c.Score, 1.5))))
            .Deny("Delete", article, Condition<ArticleClaims>.Eq(c => c.Title, null))
            .Allow("Delete", article, Condition<ArticleClaims>.Op("$hasRole", true))
            .BuildDef();

        def.Name = "Articles";
        def.Description = "Line one\nline two & <markup> \"quoted\"";
        def.Meta!.Application = new Dictionary<string, object?>
        {
            ["owner"] = "team-a",
            ["tags"] = new List<object?> { 1L, 2.5, "x", null, true, "007" },
        };
        def.Tests = new List<PolicyTestSuite>
        {
            new()
            {
                Name = "suite",
                Cases =
                {
                    new PolicyTestCase
                    {
                        Name = "owner",
                        Check = new PolicyTestCheck("Update", "Article", new Dictionary<string, object?> { ["ownerId"] = 1L }),
                        Expected = true,
                    },
                    new PolicyTestCase { Check = new PolicyTestCheck("Update", "Article", null), Expected = false },
                },
            },
        };
        return def;
    }

    [Theory]
    [MemberData(nameof(Formats))]
    public void RoundTripsARichDefinitionWithoutLosingAnything(Format format)
    {
        var original = RichDefinition();

        var text = format.Write(original);
        var restored = format.Read(text);

        Assert.True(JsonNode.DeepEquals(PolicyJson.Write(original), PolicyJson.Write(restored)), text);
    }

    [Theory]
    [MemberData(nameof(Formats))]
    public void ARestoredPolicyEvaluatesLikeTheOriginal(Format format)
    {
        var config = Config((subject, _) => subject is ArticleClaims);
        var original = new Policy(RichDefinition(), config);
        var restored = new Policy(format.Read(format.Write(original.Definition)), config);

        var subjects = new[]
        {
            Subject.Named("Article"),
            Subject.Named("Article", new ArticleClaims(1, "open", 0, "t")),
            Subject.Named("Article", new ArticleClaims(2, "draft", 2.0, "t")),
            Subject.Named("Article", new ArticleClaims(2, "draft", 1.0, null)),
            Subject.Named("Article", new ArticleClaims(2, "closed", 9, "t")),
        };

        foreach (var action in new[] { "Read", "Update", "Delete", "Create" })
        {
            foreach (var subject in subjects)
            {
                Assert.Equal(original.Can(action, subject), restored.Can(action, subject));
            }
        }
    }

    [Theory]
    [MemberData(nameof(Formats))]
    public void AnEmptyDefinitionRoundTrips(Format format)
    {
        var restored = format.Read(format.Write(new PolicyDefinition()));

        Assert.Empty(restored.Rules);
        Assert.Equal(KeycardVersion.PolicyVersion, restored.Version);
        Assert.Null(restored.Meta);
    }

    [Theory]
    [MemberData(nameof(Formats))]
    public void KeepsAnUndeclaredWildcardDistinctFromADisabledOne(Format format)
    {
        var restored = format.Read(format.Write(new PolicyDefinition
        {
            Meta = new PolicyMeta { AnyAction = WildcardToken.Off, AnySubject = null },
        }));

        Assert.Equal(WildcardToken.Off, restored.Meta!.AnyAction);
        Assert.Null(restored.Meta.AnySubject);
    }
}
