using Keycard.Conditions;
using Microsoft.Extensions.Logging;
using Xunit;

namespace Keycard.Tests;

public class ConditionTests
{
    private sealed record Claims(long OwnerId, string Status, string[] Tags);

    private sealed class Legacy
    {
        public int owner_id = 7;

        [Keycard.Conditions.ClaimName("display_name")]
        public string DisplayName { get; init; } = "Ada";
    }

    private static bool Eval(object? subject, object? condition) => new ConditionResolver().Evaluate(subject, condition);

    [Fact]
    public void ReadsFieldsFromObjectsByExactCamelCaseOrJsonName()
    {
        var claims = new Claims(5, "open", new[] { "a", "b" });

        Assert.True(Eval(claims, Condition.Eq("OwnerId", 5).ToMap()));
        Assert.True(Eval(claims, Condition.Eq("ownerId", 5).ToMap()));
        Assert.True(Eval(claims, Condition.Has("tags", "b").ToMap()));
        Assert.True(Eval(new Legacy(), Condition.Eq("owner_id", 7).ToMap()));
        Assert.True(Eval(new Legacy(), Condition.Eq("display_name", "Ada").ToMap()));
    }

    [Fact]
    public void ReadsFieldsFromAnonymousObjects()
    {
        Assert.True(Eval(new { owner_id = 1 }, Condition.Eq("owner_id", 1).ToMap()));
    }

    [Fact]
    public void AMissingFieldFailsEverythingButABareNe()
    {
        var subject = new Dictionary<string, object?> { ["a"] = 1 };

        Assert.False(Eval(subject, Condition.Eq("b", 1).ToMap()));
        Assert.True(Eval(subject, Condition.Ne("b", 1).ToMap()));
        Assert.False(Eval(subject, new Dictionary<string, object?>
        {
            ["b"] = new Dictionary<string, object?> { ["$not"] = new Dictionary<string, object?> { ["$eq"] = 1 } },
        }));
        Assert.False(Eval(new Claims(1, "x", Array.Empty<string>()), Condition.Eq("nope", 1).ToMap()));
    }

    [Fact]
    public void NumbersCompareByValueAcrossClrTypes()
    {
        var subject = new Dictionary<string, object?> { ["n"] = 5 };

        Assert.True(Eval(subject, Condition.Eq("n", 5L).ToMap()));
        Assert.True(Eval(subject, Condition.Eq("n", 5.0).ToMap()));
        Assert.True(Eval(subject, Condition.Eq("n", 5m).ToMap()));
        Assert.False(Eval(new Dictionary<string, object?> { ["n"] = double.NaN }, Condition.Eq("n", double.NaN).ToMap()));
        Assert.False(Eval(new Dictionary<string, object?> { ["n"] = double.NaN }, Condition.Lte("n", double.NaN).ToMap()));
    }

    [Fact]
    public void ComparisonsRequireNumbersOnBothSides()
    {
        Assert.False(Eval(new Dictionary<string, object?> { ["n"] = "5" }, Condition.Gt("n", 1).ToMap()));
    }

    [Fact]
    public void DoesNotConvertTypesForEquality()
    {
        Assert.False(Eval(new Dictionary<string, object?> { ["n"] = "1" }, Condition.Eq("n", 1).ToMap()));
        Assert.False(Eval(new Dictionary<string, object?> { ["n"] = true }, Condition.Eq("n", 1).ToMap()));
    }

    [Fact]
    public void FieldNamesStartingWithADollarUseTheExplicitFieldForm()
    {
        var subject = new Dictionary<string, object?> { ["$weird"] = 1 };

        Assert.True(Eval(subject, Condition.Eq("$weird", 1).ToMap()));
    }

    [Fact]
    public void FieldConditionsReachOnlyOneLevelDeep()
    {
        var subject = new Dictionary<string, object?> { ["author"] = new Dictionary<string, object?> { ["name"] = "Alice" } };
        var nested = new Dictionary<string, object?> { ["author"] = new Dictionary<string, object?> { ["name"] = "Alice" } };

        Assert.False(Eval(subject, nested));
    }

    [Fact]
    public void EveryKeyOfAMultiKeyConditionMustMatch()
    {
        var subject = new Dictionary<string, object?> { ["status"] = "open" };

        Assert.True(Eval(subject, new Dictionary<string, object?> { ["$ne"] = null, ["status"] = "open" }));
        Assert.False(Eval(subject, new Dictionary<string, object?> { ["$ne"] = null, ["status"] = "closed" }));
    }

    [Theory]
    [InlineData("^ab", "abc", true)]
    [InlineData("^ab", "cab", false)]
    [InlineData("bc$", "abc", true)]
    [InlineData("bc$", "abc\n", false)]
    [InlineData("a*c", "xabbcx", true)]
    [InlineData("a\\*c", "a*c", true)]
    [InlineData("a\\*c", "abc", false)]
    [InlineData("a.c", "abc", false)]
    [InlineData("abc\\", "abc", true)]
    [InlineData("a^b", "a^b", false)]
    [InlineData("a$b", "a$b", false)]
    public void SubstrFollowsTheSpecPatternLanguage(string pattern, string value, bool expected)
    {
        var subject = new Dictionary<string, object?> { ["s"] = value };

        Assert.Equal(expected, Eval(subject, Condition.Substr("s", pattern).ToMap()));
    }

    [Fact]
    public void SubstrMatchesTheStringFormOfNonStrings()
    {
        Assert.True(Eval(new Dictionary<string, object?> { ["n"] = 1234 }, Condition.Substr("n", "^12").ToMap()));
        Assert.False(Eval(new Dictionary<string, object?> { ["n"] = null }, Condition.Substr("n", "").ToMap()));
    }

    [Fact]
    public void EmptyOrIsFalseAndEmptyAndIsTrue()
    {
        Assert.False(Eval(new { }, Condition.Or().ToMap()));
        Assert.True(Eval(new { }, Condition.And().ToMap()));
    }

    [Fact]
    public void ReportsTypeIssuesToTheConfiguredLogger()
    {
        var logger = new RecordingLogger();
        var resolver = new ConditionResolver(new OperatorCatalog(), logger);

        resolver.Evaluate(new Dictionary<string, object?> { ["n"] = "x" }, Condition.Gt("n", 1).ToMap());
        resolver.Evaluate(new Dictionary<string, object?> { ["n"] = 1 }, Condition.Eq("missing", 1).ToMap());

        Assert.Single(logger.Messages);
        Assert.Contains("$gt", logger.Messages[0]);
    }

    [Fact]
    public void RejectsInvalidOperatorNamesInTheBuilder()
    {
        Assert.Throws<Errors.PolicyArgumentException>(() => Condition.Op("hasRole", "x"));
    }

    private sealed class RecordingLogger : ILogger
    {
        public List<string> Messages { get; } = new();

        public IDisposable? BeginScope<TState>(TState state) where TState : notnull => null;

        public bool IsEnabled(LogLevel logLevel) => true;

        public void Log<TState>(LogLevel logLevel, EventId eventId, TState state, Exception? exception, Func<TState, Exception?, string> formatter) =>
            Messages.Add(formatter(state, exception));
    }
}
