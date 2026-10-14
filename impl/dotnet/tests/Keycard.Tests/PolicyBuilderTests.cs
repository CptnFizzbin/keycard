using Keycard.Conditions;
using Keycard.Errors;
using Xunit;

namespace Keycard.Tests;

public class PolicyBuilderTests
{
    private static class Action
    {
        public static readonly PolicyAction Read = new("Read");
        public static readonly PolicyAction Create = new("Create");
        public static readonly PolicyAction Update = new("Update");
        public static readonly PolicyAction Delete = new("Delete");
    }

    private sealed record ArticleClaims(int OwnerId, string Status);

    private sealed record Article(int AuthorId, bool Archived);

    private sealed class ArticleSubject : Subject<ArticleClaims>
    {
        public static readonly ArticleSubject Def = new();

        private ArticleSubject() : base("Article") { }

        private ArticleSubject(ArticleClaims claims) : base("Article", claims) { }

        public static ArticleSubject Of(Article article) =>
            new(new ArticleClaims(article.AuthorId, article.Archived ? "archived" : "open"));
    }

    private sealed class CommentSubject : Subject<ArticleClaims>
    {
        public static readonly CommentSubject Def = new();

        private CommentSubject() : base("Comment") { }
    }

    private static readonly Subject<ArticleClaims> Plain = new("Article");

    [Fact]
    public void BuildsAPolicyFromTypedSubjectsAndConditions()
    {
        var policy = new PolicyBuilder()
            .Allow(Action.Read, ArticleSubject.Def)
            .Allow(Action.Update, ArticleSubject.Def, Condition<ArticleClaims>.Eq(c => c.OwnerId, 1))
            .Deny(Action.Delete, ArticleSubject.Def, Condition<ArticleClaims>.Eq(c => c.Status, "archived"))
            .Build();

        Assert.True(policy.Can(Action.Read, ArticleSubject.Def));
        Assert.True(policy.Can(Action.Update, ArticleSubject.Of(new Article(1, false))));
        Assert.False(policy.Can(Action.Update, ArticleSubject.Of(new Article(2, false))));
        Assert.False(policy.Can(Action.Update, ArticleSubject.Def));
    }

    [Fact]
    public void ASubclassMapsADomainEntityToClaims()
    {
        var subject = ArticleSubject.Of(new Article(7, true));

        Assert.Equal("Article", subject.Name);
        Assert.Equal(new ArticleClaims(7, "archived"), subject.Claims);
        Assert.False(ArticleSubject.Def.HasClaims);
    }

    [Fact]
    public void WrapsClaimsWithoutASubclass()
    {
        var policy = new PolicyBuilder()
            .Allow(Action.Update, Plain, Condition<ArticleClaims>.Eq(c => c.OwnerId, 1))
            .Build();

        Assert.True(policy.Can(Action.Update, Plain.Wrap(new ArticleClaims(1, "open"))));
        Assert.False(policy.Can(Action.Update, Plain.Wrap(new ArticleClaims(2, "open"))));
    }

    [Fact]
    public void StringsConvertToActionsAndSubjects()
    {
        var policy = new PolicyBuilder().Allow("Read", "Article").Build();

        Assert.True(policy.Can("Read", "Article"));
        Assert.False(policy.Can("Delete", "Article"));
    }

    [Fact]
    public void WritesTypedConditionsUnderTheirCamelCasedFieldNames()
    {
        var def = new PolicyBuilder()
            .Allow(Action.Update, ArticleSubject.Def, Condition<ArticleClaims>.Eq(c => c.OwnerId, 1))
            .BuildDef();

        Assert.Contains("""["allow","Update","Article",{"ownerId":{"$eq":1}}]""", def.ToJson());
    }

    [Fact]
    public void TheLastMatchingRuleWins()
    {
        var policy = new PolicyBuilder()
            .Allow(Action.Delete, ArticleSubject.Def, Condition<ArticleClaims>.Eq(c => c.OwnerId, 1))
            .Deny(Action.Delete, ArticleSubject.Def, Condition<ArticleClaims>.Eq(c => c.Status, "archived"))
            .Allow(Action.Delete, ArticleSubject.Def, Condition<ArticleClaims>.Eq(c => c.OwnerId, 99))
            .Build();

        Assert.False(policy.Can(Action.Delete, ArticleSubject.Of(new Article(1, true))));
        Assert.True(policy.Can(Action.Delete, ArticleSubject.Of(new Article(99, true))));
    }

    [Fact]
    public void AllowsEveryActionInAList()
    {
        var policy = new PolicyBuilder().Allow(new[] { Action.Read, Action.Create }, ArticleSubject.Def).Build();

        Assert.True(policy.Can(Action.Read, ArticleSubject.Def));
        Assert.True(policy.Can(Action.Create, ArticleSubject.Def));
        Assert.False(policy.Can(Action.Delete, ArticleSubject.Def));
    }

    [Fact]
    public void DerivesTheMetaCatalogsFromWhatWasUsed()
    {
        var config = new KeycardConfig { Operators = new OperatorCatalog().Add("$hasRole", (_, _) => true) };
        config.Actions.Add("Archive");

        var def = new PolicyBuilder(config)
            .Allow(Action.Read, ArticleSubject.Def)
            .Allow(Action.Read, CommentSubject.Def)
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
