using System.Collections;
using System.Collections.ObjectModel;
using System.Text.Json;
using System.Text.Json.Serialization;
using Keycard.Conditions;
using Keycard.Errors;

namespace Keycard;

/// <summary>
/// The <c>PolicyDefinition</c> document shape, bound to and from JSON with
/// <see cref="FromJson"/> and <see cref="ToJson"/>. KeyCard itself never reads
/// or writes YAML; a host that wants YAML converts it to this shape first.
/// </summary>
public sealed class PolicyDefinition
{
    private IList<Rule> _rules = new List<Rule>();

    internal static readonly JsonSerializerOptions JsonOptions = new()
    {
        DefaultIgnoreCondition = JsonIgnoreCondition.WhenWritingNull,
    };

    /// <summary>Required; must be a SemVer string whose MAJOR and MINOR this implementation supports (e.g. <c>"0.1"</c>).</summary>
    [JsonPropertyName("version")]
    [JsonRequired]
    public string Version { get; set; } = KeycardVersion.PolicyVersion;

    /// <summary>Informational only - plays no role in evaluation.</summary>
    [JsonPropertyName("name")]
    public string? Name { get; set; }

    /// <summary>Informational only - plays no role in evaluation.</summary>
    [JsonPropertyName("description")]
    public string? Description { get; set; }

    [JsonPropertyName("meta")]
    public PolicyMeta? Meta { get; set; }

    /// <summary>The rules in declaration order, which is significant: the last matching rule wins.</summary>
    [JsonPropertyName("rules")]
    public IList<Rule> Rules
    {
        get => _rules;
        // A document bound with "rules: null" is an empty policy.
        set => _rules = value is null ? new List<Rule>() : new List<Rule>(value);
    }

    /// <summary>Embedded test suites; they play no role in evaluation.</summary>
    [JsonPropertyName("tests")]
    public IList<PolicyTestSuite>? Tests { get; set; }

    /// <summary>Parses a policy document from JSON.</summary>
    /// <exception cref="PolicyLoadException">The document is malformed.</exception>
    public static PolicyDefinition FromJson(string json)
    {
        try
        {
            return JsonSerializer.Deserialize<PolicyDefinition>(json, JsonOptions)
                ?? throw new PolicyLoadException("A policy document must be a JSON object.");
        }
        catch (JsonException e)
        {
            throw new PolicyLoadException($"Invalid policy document: {e.Message}", e);
        }
    }

    /// <summary>Serializes this definition to JSON.</summary>
    public string ToJson(bool indented = false) =>
        JsonSerializer.Serialize(this, new JsonSerializerOptions(JsonOptions) { WriteIndented = indented });
}

/// <summary>A policy's <c>meta</c>: wildcard tokens, vocabulary catalogs, and opaque application data.</summary>
public sealed class PolicyMeta
{
    /// <summary><c>null</c> means undeclared, so <c>"_ANY_"</c> applies.</summary>
    [JsonPropertyName("anyAction")]
    [JsonConverter(typeof(WildcardTokenConverter))]
    public WildcardToken? AnyAction { get; set; }

    /// <summary><c>null</c> means undeclared, so <c>"_ANY_"</c> applies.</summary>
    [JsonPropertyName("anySubject")]
    [JsonConverter(typeof(WildcardTokenConverter))]
    public WildcardToken? AnySubject { get; set; }

    [JsonPropertyName("actions")]
    public IList<string>? Actions { get; set; }

    [JsonPropertyName("subjects")]
    public IList<string>? Subjects { get; set; }

    [JsonPropertyName("operators")]
    public IList<string>? Operators { get; set; }

    /// <summary>Opaque host data; never inspected or rejected.</summary>
    [JsonPropertyName("application")]
    [JsonConverter(typeof(PlainJsonConverter))]
    public object? Application { get; set; }
}

/// <summary>
/// A rule, <c>[Effect, Action, Subject, Condition?]</c>, serialized
/// positionally as an array. Immutable once created, so a <see cref="Policy"/>
/// can't be bypassed by changing a rule after it was validated.
/// </summary>
[JsonConverter(typeof(RuleConverter))]
public sealed class Rule
{
    /// <param name="action">An action name, or the policy's action wildcard.</param>
    /// <param name="subject">A subject name, or the policy's subject wildcard.</param>
    /// <param name="effect">Must be <c>"allow"</c> or <c>"deny"</c>; anything else is rejected when a <see cref="Policy"/> loads the rule.</param>
    /// <param name="conditions">Copied deeply; <c>null</c> makes the rule unconditional.</param>
    public Rule(string effect, string action, string subject, IReadOnlyDictionary<string, object?>? conditions = null)
    {
        Effect = effect;
        Action = action;
        Subject = subject;
        Conditions = conditions is null ? null : (IReadOnlyDictionary<string, object?>)Freeze(conditions)!;
    }

    public string Effect { get; }

    public string Action { get; }

    public string Subject { get; }

    public IReadOnlyDictionary<string, object?>? Conditions { get; }

    // Not a framework immutable copy: an explicit { $eq: null } must survive.
    private static object? Freeze(object? value)
    {
        switch (value)
        {
            case Condition condition:
                return Freeze(condition.ToMap());
            case IDictionary map:
            {
                var copy = new Dictionary<string, object?>();
                foreach (DictionaryEntry entry in map) copy[Convert.ToString(entry.Key)!] = Freeze(entry.Value);
                return new ReadOnlyDictionary<string, object?>(copy);
            }
            case IReadOnlyDictionary<string, object?> map:
            {
                var copy = new Dictionary<string, object?>();
                foreach (var (key, item) in map) copy[key] = Freeze(item);
                return new ReadOnlyDictionary<string, object?>(copy);
            }
            case string:
                return value;
            case IEnumerable sequence:
            {
                var copy = new List<object?>();
                foreach (var item in sequence) copy.Add(Freeze(item));
                return new ReadOnlyCollection<object?>(copy);
            }
            default:
                return value;
        }
    }
}

/// <summary>A named group of embedded test cases.</summary>
public sealed class PolicyTestSuite
{
    [JsonPropertyName("name")]
    public string Name { get; set; } = "";

    [JsonPropertyName("description")]
    public string? Description { get; set; }

    [JsonPropertyName("cases")]
    public IList<PolicyTestCase> Cases { get; set; } = new List<PolicyTestCase>();
}

/// <summary>One expected <c>can</c> outcome.</summary>
public sealed class PolicyTestCase
{
    [JsonPropertyName("name")]
    public string? Name { get; set; }

    /// <summary><c>[Action, Subject]</c> or <c>[Action, Subject, SubjectClaims]</c>.</summary>
    [JsonPropertyName("check")]
    public PolicyTestCheck Check { get; set; } = new("", "", null);

    [JsonPropertyName("expected")]
    public bool Expected { get; set; }
}

/// <summary>The arguments of a test case's <c>can</c> call; <see cref="Claims"/> is <c>null</c> for a bare type check.</summary>
[JsonConverter(typeof(PolicyTestCheckConverter))]
public sealed record PolicyTestCheck(string Action, string Subject, object? Claims);
