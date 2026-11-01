using Keycard.Conditions;
using Keycard.Errors;
using Xunit;

namespace Keycard.Tests;

public class PolicyValidationTests
{
    private static Policy Load(string json, KeycardConfig? config = null) =>
        new(PolicyJson.FromJson(json), config);

    // --- version ---

    [Theory]
    [InlineData("0.1")]
    [InlineData("0.1.0")]
    [InlineData("0.1.7")]
    [InlineData("0.0.9")]
    public void AcceptsSupportedVersionsAndIgnoresPatch(string version)
    {
        Load($$"""{ "version": "{{version}}", "rules": [] }""");
    }

    [Theory]
    [InlineData("0.2.0")]
    [InlineData("1.0.0")]
    [InlineData("not-a-version")]
    [InlineData("")]
    public void RejectsUnsupportedOrInvalidVersions(string version)
    {
        Assert.Throws<PolicyVersionException>(() => Load($$"""{ "version": "{{version}}", "rules": [] }"""));
    }

    // --- rule shapes ---

    [Fact]
    public void RejectsARuleWithoutAnActionOrSubject()
    {
        var noAction = new PolicyDefinition { Rules = { new Rule("allow", null!, "Article") } };
        var noSubject = new PolicyDefinition { Rules = { new Rule("allow", "Read", null!) } };

        Assert.Throws<PolicyLoadException>(() => new Policy(noAction));
        Assert.Throws<PolicyLoadException>(() => new Policy(noSubject));
    }

    [Fact]
    public void RejectsAnUnknownEffect()
    {
        Assert.Throws<PolicyLoadException>(() => Load("""{ "version": "0.1", "rules": [["permit", "Read", "Article"]] }"""));
    }

    [Fact]
    public void RejectsAConditionalRuleWildcardedOnBothSides()
    {
        var json = """{ "version": "0.1", "rules": [["allow", "_ANY_", "_ANY_", { "x": 1 }]] }""";

        Assert.Throws<PolicyLoadException>(() => Load(json));
    }

    [Fact]
    public void AllowsAConditionalRuleWildcardedOnOneSide()
    {
        var policy = Load("""{ "version": "0.1", "rules": [["allow", "_ANY_", "Article", { "x": 1 }]] }""");

        Assert.True(policy.Can("Read", Subject.Named("Article", new { x = 1 })));
    }

    // --- wildcards ---

    [Theory]
    [InlineData("true")]
    [InlineData("1")]
    [InlineData("[]")]
    public void RejectsAnInvalidWildcardDeclaration(string declared)
    {
        Assert.Throws<PolicyLoadException>(() =>
            Load($$"""{ "version": "0.1", "meta": { "anyAction": {{declared}} }, "rules": [] }"""));
    }

    [Fact]
    public void ParsesWildcardDeclarations()
    {
        Assert.Equal(new WildcardToken.Named("*"), WildcardToken.Of("*"));
        Assert.Equal(WildcardToken.Off, WildcardToken.Of(null));
        Assert.Equal(WildcardToken.Off, WildcardToken.Of(false));
        Assert.Throws<PolicyLoadException>(() => WildcardToken.Of(true));
        Assert.Throws<PolicyLoadException>(() => WildcardToken.Of(1));
    }

    [Fact]
    public void TheDefaultWildcardIsOrdinaryWhenDisabled()
    {
        var policy = Load("""{ "version": "0.1", "meta": { "anySubject": false }, "rules": [["allow", "Read", "_ANY_"]] }""");

        Assert.True(policy.Can("Read", "_ANY_"));
        Assert.False(policy.Can("Read", "Article"));
    }

    // --- catalogs ---

    [Fact]
    public void RejectsARuleOutsideADeclaredActionCatalog()
    {
        var json = """{ "version": "0.1", "meta": { "actions": ["Read"] }, "rules": [["allow", "Update", "Article"]] }""";

        Assert.Throws<PolicyLoadException>(() => Load(json));
    }

    [Fact]
    public void RejectsARuleOutsideADeclaredSubjectCatalog()
    {
        var json = """{ "version": "0.1", "meta": { "subjects": ["Article"] }, "rules": [["allow", "Read", "Comment"]] }""";

        Assert.Throws<PolicyLoadException>(() => Load(json));
    }

    [Fact]
    public void AllowsAWildcardAndAnUnusedCatalogEntry()
    {
        Load("""{ "version": "0.1", "meta": { "actions": ["Read", "Update"], "subjects": ["Article"] }, "rules": [["allow", "_ANY_", "_ANY_"]] }""");
    }

    [Fact]
    public void RejectsDuplicateCatalogEntries()
    {
        var json = """{ "version": "0.1", "meta": { "actions": ["Read", "Read"] }, "rules": [] }""";

        Assert.Throws<PolicyLoadException>(() => Load(json));
    }

    [Fact]
    public void SkipsCatalogChecksWhenEmitMetaIsOff()
    {
        var json = """{ "version": "0.1", "meta": { "actions": ["Read"] }, "rules": [["allow", "Update", "Article"]] }""";

        var policy = Load(json, new KeycardConfig { EmitMeta = false });

        Assert.True(policy.Can("Update", "Article"));
    }

    // --- operators ---

    [Fact]
    public void RejectsADeclaredOperatorThatIsNotRegistered()
    {
        var json = """{ "version": "0.1", "meta": { "operators": ["$hasRole"] }, "rules": [] }""";

        Assert.Throws<PolicyLoadException>(() => Load(json));
    }

    [Fact]
    public void RejectsADeclaredOperatorThatNamesABuiltin()
    {
        var json = """{ "version": "0.1", "meta": { "operators": ["$eq"] }, "rules": [] }""";

        Assert.Throws<PolicyLoadException>(() => Load(json));
    }

    [Fact]
    public void RejectsARuleUsingACustomOperatorMissingFromTheCatalog()
    {
        var config = new KeycardConfig { Operators = new OperatorCatalog().Add("$hasRole", (_, _) => true) };
        var json = """
            { "version": "0.1", "meta": { "operators": ["$other"] },
              "rules": [["allow", "Read", "Article", { "$or": [{ "$hasRole": "admin" }] }]] }
            """;
        config.Operators.Add("$other", (_, _) => true);

        Assert.Throws<PolicyLoadException>(() => Load(json, config));
    }

    [Fact]
    public void RejectsAnOperatorNameWithoutADollarPrefix()
    {
        Assert.Throws<PolicyLoadException>(() => new OperatorCatalog().Add("hasRole", (_, _) => true));
    }

    [Fact]
    public void RejectsADuplicateOperatorName()
    {
        Assert.Throws<PolicyLoadException>(() => new OperatorCatalog().Add("$eq", (_, _) => true));
        Assert.Throws<PolicyLoadException>(() => new OperatorCatalog().Add("$x", (_, _) => true).Add("$x", (_, _) => true));
    }

    [Fact]
    public void CustomOperatorsCanRecurseIntoTheConditionLanguage()
    {
        var operators = new OperatorCatalog().Add("$allOf", (subject, value, ctx) =>
            value is IEnumerable<object?> conditions && conditions.All(c => ctx.ResolveSubcondition(subject, c)));
        var policy = Load(
            """{ "version": "0.1", "rules": [["allow", "Read", "Article", { "$allOf": [{ "a": 1 }, { "b": 2 }] }]] }""",
            new KeycardConfig { Operators = operators });

        Assert.True(policy.Can("Read", Subject.Named("Article", new Dictionary<string, object?> { ["a"] = 1, ["b"] = 2 })));
        Assert.False(policy.Can("Read", Subject.Named("Article", new Dictionary<string, object?> { ["a"] = 1, ["b"] = 3 })));
    }

    // --- evaluation ---

    [Fact]
    public void RequireThrowsOnlyWhenDenied()
    {
        var policy = Load("""{ "version": "0.1", "rules": [["allow", "Read", "Article"]] }""");

        policy.Require("Read", "Article");
        var ex = Assert.Throws<PolicyException>(() => policy.Require("Delete", "Article"));
        Assert.Contains("Delete", ex.Message);
    }

    [Fact]
    public void EvaluationIgnoresLaterMutationOfTheDefinition()
    {
        var definition = PolicyJson.FromJson("""{ "version": "0.1", "rules": [["allow", "Read", "Article"]] }""");
        var policy = new Policy(definition);

        definition.Rules.Add(new Rule("allow", "_ANY_", "_ANY_"));
        definition.Meta = new PolicyMeta { AnyAction = WildcardToken.Off };

        Assert.False(policy.Can("Delete", "Comment"));
    }

    [Fact]
    public void ARuleCannotBeChangedThroughTheConditionsItWasBuiltFrom()
    {
        var conditions = new Dictionary<string, object?> { ["owner_id"] = 1L };
        var rule = new Rule("allow", "Read", "Article", conditions);

        conditions["owner_id"] = 2L;

        Assert.Equal(1L, rule.Conditions!["owner_id"]);
    }
}
