using Keycard.Conditions;
using Keycard.Errors;
using Xunit;

namespace Keycard.Tests;

public class PolicyBuilderTests
{
    private static readonly Subject<ArticleClaims> Article = new("Article");

    private sealed record ArticleClaims(int OwnerId, string Status);

    [Fact]
    public void BuildsAPolicyThatEvaluatesItsRules()
    {
        var policy = new PolicyBuilder()
            .Allow("Read", "Article")
            .Allow("Update", Article, Condition.Eq("ownerId", 1))
            .Deny("Delete", Article, Condition.Eq("status", "archived"))
            .Build();

        Assert.True(policy.Can("Read", "Article"));
        Assert.True(policy.Can("Update", Article.Wrap(new ArticleClaims(1, "open"))));
        Assert.False(policy.Can("Update", Article.Wrap(new ArticleClaims(2, "open"))));
        Assert.False(policy.Can("Update", Article));
    }

    [Fact]
    public void TheLastMatchingRuleWins()
    {
        var policy = new PolicyBuilder()
            .Allow("Delete", Article, Condition.Eq("ownerId", 1))
            .Deny("Delete", Article, Condition.Eq("status", "archived"))
            .Allow("Delete", Article, Condition.Eq("ownerId", 99))
            .Build();

        Assert.False(policy.Can("Delete", Article.Wrap(new ArticleClaims(1, "archived"))));
        Assert.True(policy.Can("Delete", Article.Wrap(new ArticleClaims(99, "archived"))));
    }

    [Fact]
    public void AllowsEveryActionInAList()
    {
        var policy = new PolicyBuilder().Allow(new[] { "Read", "Create" }, "Article").Build();

        Assert.True(policy.Can("Read", "Article"));
        Assert.True(policy.Can("Create", "Article"));
        Assert.False(policy.Can("Delete", "Article"));
    }

    [Fact]
    public void DerivesTheMetaCatalogsFromWhatWasUsed()
    {
        var config = new KeycardConfig { Operators = new OperatorCatalog().Add("$hasRole", (_, _) => true) };
        config.Actions.Add("Archive");

        var def = new PolicyBuilder(config)
            .Allow("Read", "Article")
            .Allow("Read", "Comment")
            .Build().Definition;

        Assert.Equal(new[] { "Read", "Archive" }, def.Meta!.Actions);
        Assert.Equal(new[] { "Article", "Comment" }, def.Meta.Subjects);
        Assert.Equal(new[] { "$hasRole" }, def.Meta.Operators);
        Assert.Equal(KeycardVersion.PolicyVersion, def.Version);
    }

    [Fact]
    public void KeepsConfiguredWildcardsInMetaEvenWhenEmitMetaIsOff()
    {
        var config = new KeycardConfig { EmitMeta = false }.UseAnyAction("*").DisableAnySubject();

        var def = new PolicyBuilder(config).Allow("*", "_ANY_").BuildDef();

        Assert.Equal(new WildcardToken.Named("*"), def.Meta!.AnyAction);
        Assert.Equal(WildcardToken.Off, def.Meta.AnySubject);
        Assert.Null(def.Meta.Actions);
        Assert.Contains("\"anySubject\":null", def.ToJson());
    }

    [Fact]
    public void RejectsAConditionalRuleWildcardedOnBothSides()
    {
        var builder = new PolicyBuilder();

        Assert.Throws<PolicyArgumentException>(() => builder.Allow("_ANY_", "_ANY_", Condition.Eq("a", 1)));
    }

    [Fact]
    public void AllowsAConditionWhenTheDefaultWildcardIsRenamed()
    {
        var builder = new PolicyBuilder(new KeycardConfig().UseAnyAction("*").UseAnySubject("**"));

        builder.Allow("_ANY_", "_ANY_", Condition.Eq("a", 1));
        Assert.Throws<PolicyArgumentException>(() => builder.Allow("*", "**", Condition.Eq("a", 1)));
    }

    [Fact]
    public void ABuiltDefinitionIsIsolatedFromLaterBuilderCalls()
    {
        var builder = new PolicyBuilder().Allow("Read", "Article");
        var def = builder.BuildDef();

        builder.Allow("Delete", "Article");

        Assert.Single(def.Rules);
    }
}
