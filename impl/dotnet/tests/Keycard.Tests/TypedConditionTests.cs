using System.Text.Json.Serialization;
using Keycard.Conditions;
using Keycard.Errors;
using Xunit;

namespace Keycard.Tests;

public class TypedConditionTests
{
    private sealed record Claims(
        int OwnerId,
        string Status,
        double Score,
        string[] Tags,
        List<int> Ids,
        string? Title = null)
    {
        [JsonPropertyName("created_at")]
        public long CreatedAt { get; init; }
    }

    private static readonly Claims Sample = new(5, "open", 2.5, new[] { "a", "b" }, new List<int> { 1, 2 }, "Hello World")
    {
        CreatedAt = 100,
    };

    private static bool Matches(Condition<Claims> condition, Claims? claims = null) =>
        new ConditionResolver().Evaluate(claims ?? Sample, condition);

    [Fact]
    public void ComparesTypedFields()
    {
        Assert.True(Matches(Condition<Claims>.Eq(c => c.OwnerId, 5)));
        Assert.False(Matches(Condition<Claims>.Eq(c => c.OwnerId, 6)));
        Assert.True(Matches(Condition<Claims>.Ne(c => c.Status, "closed")));
        Assert.True(Matches(Condition<Claims>.Gt(c => c.Score, 2.0)));
        Assert.True(Matches(Condition<Claims>.Gte(c => c.OwnerId, 5)));
        Assert.True(Matches(Condition<Claims>.Lt(c => c.OwnerId, 6)));
        Assert.True(Matches(Condition<Claims>.Lte(c => c.Score, 2.5)));
        Assert.False(Matches(Condition<Claims>.Gt(c => c.OwnerId, 5)));
    }

    [Fact]
    public void ChecksMembershipAndContainment()
    {
        Assert.True(Matches(Condition<Claims>.In(c => c.Status, "draft", "open")));
        Assert.False(Matches(Condition<Claims>.In(c => c.Status, "draft", "closed")));
        Assert.True(Matches(Condition<Claims>.Has(c => c.Tags, "b")));
        Assert.True(Matches(Condition<Claims>.Has(c => c.Ids, 2)));
        Assert.False(Matches(Condition<Claims>.Has(c => c.Tags, "z")));
    }

    [Fact]
    public void MatchesSubstringPatterns()
    {
        Assert.True(Matches(Condition<Claims>.Substr(c => c.Title, "^Hello")));
        Assert.False(Matches(Condition<Claims>.Substr(c => c.Title, "^World")));
    }

    [Fact]
    public void CombinesConditions()
    {
        var owner = Condition<Claims>.Eq(c => c.OwnerId, 5);
        var closed = Condition<Claims>.Eq(c => c.Status, "closed");

        Assert.True(Matches(Condition<Claims>.Or(owner, closed)));
        Assert.False(Matches(Condition<Claims>.And(owner, closed)));
        Assert.True(Matches(Condition<Claims>.Not(closed)));
    }

    [Fact]
    public void UsesTheJsonPropertyNameWhenPresent()
    {
        var condition = Condition<Claims>.Eq(c => c.CreatedAt, 100L);

        Assert.True(condition.ToMap().ContainsKey("created_at"));
        Assert.True(Matches(condition));
    }

    [Fact]
    public void WritesCamelCasedFieldNames()
    {
        Assert.Equal(new[] { "ownerId" }, Condition<Claims>.Eq(c => c.OwnerId, 1).ToMap().Keys);
    }

    [Fact]
    public void ScopesCustomOperatorsToAField()
    {
        var map = Condition<Claims>.Op(c => c.Status, "$startsWith", "op").ToMap();

        Assert.Equal("op", ((IDictionary<string, object?>)map["status"]!)["$startsWith"]);
        Assert.Throws<PolicyArgumentException>(() => Condition<Claims>.Op(c => c.Status, "startsWith", "op"));
    }

    [Fact]
    public void RejectsASelectorThatIsNotADirectMember()
    {
        Assert.Throws<PolicyArgumentException>(() => Condition<Claims>.Eq(c => c.Tags.Length, 2));
        Assert.Throws<PolicyArgumentException>(() => Condition<Claims>.Eq(c => c.OwnerId + 1, 2));
    }

    [Fact]
    public void ARuleBuiltFromATypedConditionSurvivesAJsonRoundTrip()
    {
        var def = new PolicyBuilder()
            .Allow("Read", new Subject<Claims>("Article"), Condition<Claims>.In(c => c.Status, "open", "draft"))
            .BuildDef();

        var policy = new Policy(PolicyDefinition.FromJson(def.ToJson()));

        Assert.True(policy.Can("Read", Subject.Named("Article", Sample)));
        Assert.False(policy.Can("Read", Subject.Named("Article", Sample with { Status = "closed" })));
    }
}
