using Keycard.Conditions;
using Keycard.Errors;

namespace Keycard;

/// <summary>
/// Evaluates checks against a <see cref="PolicyDefinition"/>. Rules are
/// validated once, when the policy is constructed.
/// </summary>
/// <example>
/// <code>
/// var policy = new PolicyBuilder()
///     .Allow("Read", "Article")
///     .Allow("Update", "Article", Condition.Eq("owner_id", 1))
///     .Build();
///
/// policy.Can("Update", Subject.Of("Article", new { owner_id = 1 })); // true
/// </code>
/// </example>
public sealed class Policy
{
    private readonly ConditionResolver _resolver;

    // Snapshotted at construction, so evaluation only ever sees the rules and
    // wildcard tokens that were validated here - later mutation of the
    // (mutable) PolicyDefinition can't bypass validation.
    private readonly Rule[] _rules;
    private readonly WildcardToken _anyAction;
    private readonly WildcardToken _anySubject;

    /// <summary>The definition this policy was constructed from; its <c>Version</c> is preserved as-is.</summary>
    public PolicyDefinition Definition { get; }

    /// <exception cref="PolicyVersionException">The definition's version is invalid or unsupported.</exception>
    /// <exception cref="PolicyLoadException">The definition is malformed or violates its own <c>meta</c>.</exception>
    public Policy(PolicyDefinition definition, KeycardConfig? config = null)
    {
        ArgumentNullException.ThrowIfNull(definition);
        config ??= new KeycardConfig();

        KeycardVersion.AssertSupported(definition.Version);

        Definition = definition;
        _resolver = new ConditionResolver(config.Operators, config.Logger);
        _rules = definition.Rules.ToArray();
        _anyAction = ResolveWildcard(definition.Meta?.AnyAction);
        _anySubject = ResolveWildcard(definition.Meta?.AnySubject);

        // Structural validity is what evaluation itself relies on, so it's
        // checked regardless of EmitMeta - only the catalog checks are gated.
        ValidateRuleShapes();
        if (config.EmitMeta)
        {
            ValidateCatalogs(definition.Meta, config);
            ValidateRuleCatalogs(definition.Meta, config);
        }
    }

    /// <summary>
    /// Returns whether <paramref name="action"/> is allowed on <paramref name="subject"/>:
    /// the last-declared rule whose action, subject, and condition all match
    /// decides, and no match means deny. A subject without claims (a bare type
    /// check) never matches a conditional rule.
    /// </summary>
    public bool Can(string action, Subject subject)
    {
        ArgumentNullException.ThrowIfNull(action);
        ArgumentNullException.ThrowIfNull(subject);

        for (var i = _rules.Length - 1; i >= 0; i--)
        {
            var rule = _rules[i];

            if (!WildcardToken.Matches(action, rule.Action, _anyAction)) continue;
            if (!WildcardToken.Matches(subject.Name, rule.Subject, _anySubject)) continue;

            if (rule.Conditions is not null)
            {
                // A bare check has no claims for the condition to inspect; without
                // this guard, a condition such as { x: { $ne: 1 } } would match it.
                if (!subject.HasClaims) continue;
                if (!_resolver.Evaluate(subject.Claims, rule.Conditions)) continue;
            }

            return rule.Effect == "allow";
        }

        return false;
    }

    /// <summary>The exact negation of <see cref="Can"/>.</summary>
    public bool Cannot(string action, Subject subject) => !Can(action, subject);

    /// <summary>Returns normally when <see cref="Can"/> is <c>true</c>.</summary>
    /// <exception cref="PolicyException">The check is denied.</exception>
    public void Require(string action, Subject subject)
    {
        if (!Can(action, subject))
        {
            throw new PolicyException($"Access denied: cannot {action} on {subject.Name}");
        }
    }

    private static WildcardToken ResolveWildcard(WildcardToken? declared) => WildcardToken.OrDefault(declared);

    private void ValidateRuleShapes()
    {
        foreach (var rule in _rules)
        {
            if (rule is null) throw new PolicyLoadException("Malformed rule tuple: a rule must not be null.");

            if (rule.Effect is not ("allow" or "deny"))
            {
                throw new PolicyLoadException(
                    $"Malformed rule tuple: effect must be \"allow\" or \"deny\", got {rule.Effect ?? "null"}.");
            }

            if (rule.Action is null) throw new PolicyLoadException("Malformed rule tuple: action must be a string, got null.");
            if (rule.Subject is null) throw new PolicyLoadException("Malformed rule tuple: subject must be a string, got null.");

            if (rule.Conditions is not null
                && WildcardToken.IsWildcard(_anyAction, rule.Action)
                && WildcardToken.IsWildcard(_anySubject, rule.Subject))
            {
                throw new PolicyLoadException(
                    $"Rule [{rule.Effect}, {rule.Action}, {rule.Subject}] is wildcarded on both the action and the subject"
                    + " but carries a condition - this MUST be unconditional.");
            }
        }
    }

    private void ValidateCatalogs(PolicyMeta? meta, KeycardConfig config)
    {
        AssertUnique(meta?.Actions, "meta.actions");
        AssertUnique(meta?.Subjects, "meta.subjects");
        AssertUnique(meta?.Operators, "meta.operators");

        foreach (var name in meta?.Operators ?? Array.Empty<string>())
        {
            if (OperatorCatalog.BuiltinNames.Contains(name))
            {
                throw new PolicyLoadException($"meta.operators lists \"{name}\", which is a built-in operator.");
            }
        }

        if (meta?.Operators is { } declared) _resolver.AssertAllRegistered(declared);
    }

    private void ValidateRuleCatalogs(PolicyMeta? meta, KeycardConfig config)
    {
        var actions = Catalog(meta?.Actions, config.Actions);
        var subjects = Catalog(meta?.Subjects, config.Subjects);
        var operators = meta?.Operators is { } ops ? new HashSet<string>(ops) : null;

        foreach (var rule in _rules)
        {
            if (actions is not null && !WildcardToken.IsWildcard(_anyAction, rule.Action) && !actions.Contains(rule.Action))
            {
                throw new PolicyLoadException($"Rule action \"{rule.Action}\" is not covered by meta.actions.");
            }

            if (subjects is not null && !WildcardToken.IsWildcard(_anySubject, rule.Subject) && !subjects.Contains(rule.Subject))
            {
                throw new PolicyLoadException($"Rule subject \"{rule.Subject}\" is not covered by meta.subjects.");
            }

            if (operators is null || rule.Conditions is null) continue;

            var used = new HashSet<string>();
            CollectCustomOperators(rule.Conditions, used);
            foreach (var op in used)
            {
                if (!operators.Contains(op))
                {
                    throw new PolicyLoadException($"Rule uses custom operator \"{op}\" not covered by meta.operators.");
                }
            }
        }
    }

    private static HashSet<string>? Catalog(IList<string>? declared, IList<string> configured)
    {
        if ((declared is null || declared.Count == 0) && configured.Count == 0) return null;

        var catalog = new HashSet<string>(configured);
        if (declared is not null) catalog.UnionWith(declared);
        return catalog;
    }

    private static void AssertUnique(IList<string>? names, string where)
    {
        if (names is null) return;

        var seen = new HashSet<string>();
        foreach (var name in names)
        {
            if (!seen.Add(name)) throw new PolicyLoadException($"{where} lists \"{name}\" more than once.");
        }
    }

    private static void CollectCustomOperators(object? condition, HashSet<string> found)
    {
        if (condition is not System.Collections.Generic.IDictionary<string, object?> map) return;

        foreach (var (key, value) in map)
        {
            if (!key.StartsWith('$'))
            {
                CollectCustomOperators(value, found);
            }
            else if (key is "$or" or "$and")
            {
                if (value is System.Collections.IEnumerable list and not string)
                {
                    foreach (var item in list) CollectCustomOperators(item, found);
                }
            }
            else if (key == "$not")
            {
                CollectCustomOperators(value, found);
            }
            else if (key == "$field" && value is IList<object?> { Count: 2 } tuple)
            {
                CollectCustomOperators(tuple[1], found);
            }
            else if (!OperatorCatalog.BuiltinNames.Contains(key))
            {
                found.Add(key);
            }
        }
    }
}
