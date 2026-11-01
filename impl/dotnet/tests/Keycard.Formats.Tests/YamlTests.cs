using Keycard.Errors;
using Keycard.Yaml;
using Xunit;

namespace Keycard.Formats.Tests;

public class YamlTests
{
    [Fact]
    public void LoadsAHandWrittenDocument()
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

        var definition = PolicyYaml.Parse(yaml);
        var policy = new Policy(definition);

        Assert.Equal(WildcardToken.Off, definition.Meta!.AnySubject);
        Assert.True(definition.Tests![0].Cases[0].Expected);
        Assert.True(policy.Can("Create", "Article"));
        Assert.True(policy.Can("Update", Subject.Named("Article", new { owner_id = 1 })));
        Assert.False(policy.Can("Update", Subject.Named("Article", new { owner_id = 2 })));
    }

    [Fact]
    public void OnlyUnquotedScalarsAreTyped()
    {
        const string yaml = """
            version: "0.1"
            rules:
              - [allow, Read, Article, { a: 1, b: "1", c: true, d: "true", e: no, f: null, g: 1.5 }]
            """;

        var conditions = PolicyYaml.Parse(yaml).Rules[0].Conditions!;

        Assert.Equal(1L, conditions["a"]);
        Assert.Equal("1", conditions["b"]);
        Assert.Equal(true, conditions["c"]);
        Assert.Equal("true", conditions["d"]);
        Assert.Equal("no", conditions["e"]);
        Assert.Null(conditions["f"]);
        Assert.Equal(1.5, conditions["g"]);
    }

    [Fact]
    public void QuotesStringsSoTheyReadBackAsStrings()
    {
        var definition = new PolicyDefinition
        {
            Rules = { new Rule("allow", "Read", "Article", new Dictionary<string, object?> { ["a"] = "1", ["b"] = "null", ["c"] = "x: y" }) },
        };

        var conditions = PolicyYaml.Parse(PolicyYaml.Serialize(definition)).Rules[0].Conditions!;

        Assert.Equal("1", conditions["a"]);
        Assert.Equal("null", conditions["b"]);
        Assert.Equal("x: y", conditions["c"]);
    }

    [Theory]
    [InlineData("")]
    [InlineData("a: [")]
    [InlineData("- a\n- b")]
    [InlineData("version: \"0.1\"\n---\nversion: \"0.1\"")]
    [InlineData("rules: []")]
    [InlineData("version: \"0.1\"\nrules: x")]
    [InlineData("? [a]\n: b")]
    public void ReportsABadDocumentAsALoadError(string yaml)
    {
        Assert.Throws<PolicyLoadException>(() => PolicyYaml.Parse(yaml));
    }
}
