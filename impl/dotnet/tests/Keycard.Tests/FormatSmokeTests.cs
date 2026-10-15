using System.Text.Json.Nodes;
using Keycard.Conditions;
using Keycard.Tests.Integration;
using Xunit;

namespace Keycard.Tests;

/// <summary>
/// Smoke tests that a policy document survives JSON, XML, and YAML. Keycard only
/// converts to and from <see cref="JsonObject"/>; each format here is application
/// code that gets to or from that object through plain data.
/// </summary>
public class FormatSmokeTests
{
    public sealed record Format(string Name, Func<PolicyDefinition, string> Write, Func<string, PolicyDefinition> Read)
    {
        public override string ToString() => Name;
    }

    public static IEnumerable<object[]> Formats()
    {
        yield return new object[]
        {
            new Format("JSON", PolicyJson.ToJson, PolicyJson.FromJson),
        };
        yield return new object[]
        {
            new Format(
                "XML",
                def => XmlPlain.Dump(PlainTree.FromNode(def.ToJsonObject())),
                xml => PolicyJson.FromPlain(XmlPlain.Load(xml)!)),
        };
        yield return new object[]
        {
            new Format(
                "YAML",
                def => YamlPlain.Dump(PlainTree.FromNode(def.ToJsonObject())),
                yaml => PolicyJson.FromPlain(YamlPlain.Parse(yaml).Single()!)),
        };
    }

    private sealed record ArticleClaims(int OwnerId, string Status, double Score, string? Title);

    private static PolicyDefinition RichDefinition()
    {
        var config = new KeycardConfig { Operators = new OperatorCatalog().Add("$hasRole", (_, _) => true) }
            .UseAnyAction("*")
            .DisableAnySubject();

        var def = new PolicyBuilder(config)
            .Allow("Read", new Subject<ArticleClaims>("Article"))
            .Allow("Update", new Subject<ArticleClaims>("Article"), Condition<ArticleClaims>.Or(
                Condition<ArticleClaims>.Eq(c => c.OwnerId, 1),
                Condition<ArticleClaims>.And(
                    Condition<ArticleClaims>.In(c => c.Status, "draft", "published", "1", "true"),
                    Condition<ArticleClaims>.Gte(c => c.Score, 1.5))))
            .Deny("Delete", new Subject<ArticleClaims>("Article"), Condition<ArticleClaims>.Eq(c => c.Title, null))
            .Allow("Delete", new Subject<ArticleClaims>("Article"), Condition<ArticleClaims>.Op("$hasRole", true))
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

        Assert.True(JsonNode.DeepEquals(original.ToJsonObject(), restored.ToJsonObject()), text);
    }

    [Theory]
    [MemberData(nameof(Formats))]
    public void ARestoredPolicyEvaluatesLikeTheOriginal(Format format)
    {
        var config = new KeycardConfig { Operators = new OperatorCatalog().Add("$hasRole", (s, v) => s is ArticleClaims) }
            .UseAnyAction("*")
            .DisableAnySubject();
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

    [Fact]
    public void LoadsAHandWrittenYamlDocument()
    {
        const string yaml = """
            version: "0.1"
            name: Articles
            meta:
              actions: [Create, Update, Delete]
              subjects: [Article]
              anySubject: null
            rules:
              - [allow, Create, Article]
              - [allow, Update, Article, { owner_id: 1 }]
              - [deny, Delete, Article, { status: "archived" }]
            tests:
              - name: ownership
                cases:
                  - check: [Update, Article, { owner_id: 1 }]
                    expected: true
            """;

        var definition = PolicyJson.FromPlain(YamlPlain.Parse(yaml).Single()!);
        var policy = new Policy(definition);

        Assert.Equal(WildcardToken.Off, definition.Meta!.AnySubject);
        Assert.True(definition.Tests![0].Cases[0].Expected);
        Assert.True(policy.Can("Create", "Article"));
        Assert.True(policy.Can("Update", Subject.Named("Article", new { owner_id = 1 })));
        Assert.False(policy.Can("Update", Subject.Named("Article", new { owner_id = 2 })));
    }

    [Fact]
    public void LoadsAHandWrittenXmlDocument()
    {
        const string xml = """
            <map>
              <item key="version"><string>0.1</string></item>
              <item key="rules">
                <list>
                  <list><string>allow</string><string>Read</string><string>Article</string></list>
                  <list>
                    <string>allow</string><string>Update</string><string>Article</string>
                    <map><item key="owner_id"><number>1</number></item></map>
                  </list>
                </list>
              </item>
            </map>
            """;

        var policy = new Policy(PolicyJson.FromPlain(XmlPlain.Load(xml)!));

        Assert.True(policy.Can("Read", "Article"));
        Assert.True(policy.Can("Update", Subject.Named("Article", new { owner_id = 1 })));
        Assert.False(policy.Can("Update", Subject.Named("Article", new { owner_id = 2 })));
    }

    [Fact]
    public void ADocumentWithAnEmptyRulesListLoadsInEveryFormat()
    {
        var empty = new PolicyDefinition();

        foreach (var args in Formats())
        {
            var format = (Format)args[0];
            Assert.Empty(format.Read(format.Write(empty)).Rules);
        }
    }
}
