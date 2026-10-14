using Keycard.Conditions;
using Keycard.Errors;

namespace Keycard;

/// <summary>
/// Builds a <see cref="PolicyDefinition"/> rule by rule. <see cref="BuildDef"/>
/// derives <c>meta.actions</c>, <c>meta.subjects</c>, and <c>meta.operators</c>
/// from what <see cref="Allow(string, Subject, Condition?)"/>/<see cref="Deny(string, Subject, Condition?)"/>
/// used and what the config registered, so there is no hand-maintained catalog
/// to keep in sync. Rules keep the order they were added in; the last matching
/// rule wins, so add general rules first and the rules that override them later.
/// </summary>
public sealed class PolicyBuilder
{
    private readonly List<Rule> _rules = new();
    private readonly HashSet<string> _actionsUsed = new();
    private readonly List<string> _actionOrder = new();
    private readonly HashSet<string> _subjectsUsed = new();
    private readonly List<string> _subjectOrder = new();
    private readonly KeycardConfig _config;

    public PolicyBuilder(KeycardConfig? config = null) => _config = config ?? new KeycardConfig();

    /// <summary>Adds a rule allowing <paramref name="action"/> on <paramref name="subject"/>, where <paramref name="condition"/> holds if given.</summary>
    /// <exception cref="PolicyArgumentException">The rule is wildcarded on both sides and carries a condition.</exception>
    public PolicyBuilder Allow(string action, Subject subject, Condition? condition = null) =>
        AddRule("allow", action, subject, condition);

    /// <summary>Adds one <c>allow</c> rule per action, in order.</summary>
    public PolicyBuilder Allow(IEnumerable<string> actions, Subject subject, Condition? condition = null)
    {
        foreach (var action in actions) Allow(action, subject, condition);
        return this;
    }

    /// <summary>Adds a rule denying <paramref name="action"/> on <paramref name="subject"/>, where <paramref name="condition"/> holds if given.</summary>
    /// <exception cref="PolicyArgumentException">The rule is wildcarded on both sides and carries a condition.</exception>
    public PolicyBuilder Deny(string action, Subject subject, Condition? condition = null) =>
        AddRule("deny", action, subject, condition);

    /// <summary>Adds one <c>deny</c> rule per action, in order.</summary>
    public PolicyBuilder Deny(IEnumerable<string> actions, Subject subject, Condition? condition = null)
    {
        foreach (var action in actions) Deny(action, subject, condition);
        return this;
    }

    /// <summary>Builds a <see cref="Policy"/> from the current rules.</summary>
    public Policy Build() => new(BuildDef(), _config);

    /// <summary>Builds a standalone definition; later calls to this builder don't affect it.</summary>
    public PolicyDefinition BuildDef() => new()
    {
        Rules = _rules,
        Meta = BuildMeta(),
    };

    private PolicyMeta? BuildMeta()
    {
        var hasWildcards = _config.AnyAction is not null || _config.AnySubject is not null;
        if (!_config.EmitMeta && !hasWildcards) return null;

        // Emitted regardless of EmitMeta: evaluation depends on the wildcard
        // tokens, so dropping them would silently revert to "_ANY_".
        var meta = new PolicyMeta { AnyAction = _config.AnyAction, AnySubject = _config.AnySubject };
        if (!_config.EmitMeta) return meta;

        meta.Actions = _actionOrder.Concat(_config.Actions.Where(a => !_actionsUsed.Contains(a))).Distinct().ToList();
        meta.Subjects = _subjectOrder.Concat(_config.Subjects.Where(s => !_subjectsUsed.Contains(s))).Distinct().ToList();
        meta.Operators = _config.Operators.CustomNames.ToList();
        return meta;
    }

    private PolicyBuilder AddRule(string effect, string action, Subject subject, Condition? condition)
    {
        ArgumentNullException.ThrowIfNull(action);
        ArgumentNullException.ThrowIfNull(subject);

        if (condition is not null)
        {
            // The spec requires the builder to reject a conditional rule
            // wildcarded on both sides at the call site, not later in new Policy(...).
            var anyAction = WildcardToken.OrDefault(_config.AnyAction);
            var anySubject = WildcardToken.OrDefault(_config.AnySubject);

            if (WildcardToken.IsWildcard(anyAction, action) && WildcardToken.IsWildcard(anySubject, subject.Name))
            {
                throw new PolicyArgumentException(
                    $"A rule wildcarded on both the action (\"{action}\") and the subject (\"{subject.Name}\") MUST NOT carry a condition.");
            }
        }

        if (_actionsUsed.Add(action)) _actionOrder.Add(action);
        if (_subjectsUsed.Add(subject.Name)) _subjectOrder.Add(subject.Name);

        _rules.Add(new Rule(effect, action, subject.Name, condition?.ToMap()));
        return this;
    }
}
