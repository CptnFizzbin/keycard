using Keycard.Conditions;
using Xunit;

namespace Keycard.Tests.Integration;

/// <summary>
/// Runs the shared, spec-derived fixtures under <c>test/fixtures</c> (see
/// <c>test/fixtures/v0/README.md</c>) that every implementation MUST pass.
/// </summary>
public class ConformanceFixtureTests
{
    private static readonly string FixturesDir = FindFixturesDir();

    public static IEnumerable<object[]> Cases()
    {
        foreach (var file in Directory.EnumerateFiles(FixturesDir, "*.yaml", SearchOption.AllDirectories).OrderBy(f => f, StringComparer.Ordinal))
        {
            var documents = YamlPlain.LoadDocuments(file);
            for (var s = 0; s < documents.Count; s++)
            {
                var suite = (Dictionary<string, object?>)documents[s]!;
                var tests = (List<object?>)suite["tests"]!;
                for (var t = 0; t < tests.Count; t++)
                {
                    var name = Path.GetFileName(file) + " > " + suite["name"] + " > " + ((Dictionary<string, object?>)tests[t]!).GetValueOrDefault("name");
                    yield return new object[] { new FixtureCase(name, file, s, t) };
                }
            }
        }
    }

    [Theory]
    [MemberData(nameof(Cases))]
    public void ResolvesExpectedResult(FixtureCase fixture)
    {
        var suite = (Dictionary<string, object?>)YamlPlain.LoadDocuments(fixture.File)[fixture.Suite]!;
        var testCase = (Dictionary<string, object?>)((List<object?>)suite["tests"]!)[fixture.Test]!;

        var definition = PolicyDefinition.FromJson(System.Text.Json.JsonSerializer.Serialize(suite));
        var config = new KeycardConfig { Operators = OperatorsFor(Path.GetFileName(fixture.File)) };
        var policy = new Policy(definition, config);

        var check = (List<object?>)testCase["check"]!;
        var subject = check.Count == 3 ? Subject.Named((string)check[1]!, check[2]!) : Subject.Named((string)check[1]!);
        var expected = (bool)testCase["expected"]!;

        Assert.Equal(expected, policy.Can((string)check[0]!, subject));
        Assert.Equal(!expected, policy.Cannot((string)check[0]!, subject));
    }

    // Only the host application can implement a custom operator; a fixture's
    // meta.operators merely declares it.
    private static OperatorCatalog OperatorsFor(string fixtureFileName)
    {
        var operators = new OperatorCatalog();
        switch (fixtureFileName)
        {
            case "11-worked-example.yaml":
                operators.Add("$hasRole", (subject, value) =>
                    subject is IDictionary<string, object?> map
                    && map.TryGetValue("roles", out var roles)
                    && roles is IEnumerable<object?> list
                    && list.Contains(value));
                break;
            case "policy-05-advanced.yaml":
                operators.Add("$startsWithUpper", (subject, value) =>
                    subject is string { Length: > 0 } text
                    && value is bool wantUpper
                    && char.IsUpper(text[0]) == wantUpper);
                break;
        }

        return operators;
    }

    private static string FindFixturesDir()
    {
        for (var dir = new DirectoryInfo(AppContext.BaseDirectory); dir is not null; dir = dir.Parent)
        {
            var candidate = Path.Combine(dir.FullName, "test", "fixtures");
            if (Directory.Exists(candidate)) return candidate;
        }

        throw new DirectoryNotFoundException("Could not find test/fixtures above " + AppContext.BaseDirectory);
    }

    public sealed record FixtureCase(string Name, string File, int Suite, int Test)
    {
        public override string ToString() => Name;
    }
}
