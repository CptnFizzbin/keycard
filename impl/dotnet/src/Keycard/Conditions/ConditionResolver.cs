using System.Collections;
using Keycard.Errors;
using Microsoft.Extensions.Logging;
using Microsoft.Extensions.Logging.Abstractions;

namespace Keycard.Conditions;

/// <summary>
/// Evaluates a condition tree against a subject, using the built-in operators
/// plus any custom operators in the <see cref="OperatorCatalog"/> it was
/// constructed with.
/// </summary>
public sealed class ConditionResolver
{
    private readonly OperatorCatalog _registry;
    private readonly ILogger _logger;
    private readonly IOperatorContext _topContext;
    private readonly IOperatorContext _nestedContext;

    public ConditionResolver() : this(new OperatorCatalog(), null) { }

    /// <param name="operators">The operators to dispatch to; <c>null</c> means the built-ins only.</param>
    /// <param name="logger">Where type-issue diagnostics go; <c>null</c> discards them.</param>
    public ConditionResolver(OperatorCatalog? operators, ILogger? logger = null)
    {
        _registry = operators ?? new OperatorCatalog();
        _logger = logger ?? NullLogger.Instance;
        _topContext = new Context(this, canNarrowField: true);
        _nestedContext = new Context(this, canNarrowField: false);
    }

    /// <summary>
    /// Returns whether <paramref name="condition"/> matches <paramref name="subject"/>.
    /// A condition that can't be meaningfully evaluated (a type mismatch, an
    /// unregistered operator) is simply <c>false</c>.
    /// </summary>
    public bool Evaluate(object? subject, object? condition) => Evaluate(subject, condition, canNarrowField: true);

    /// <summary>Throws a <see cref="PolicyLoadException"/> if any name in <paramref name="names"/> isn't registered, built-in or custom.</summary>
    public void AssertAllRegistered(IEnumerable<string> names)
    {
        foreach (var name in names)
        {
            if (!_registry.Contains(name))
            {
                throw new PolicyLoadException(
                    $"meta.operators declares \"{name}\" but no operator with that name is registered.");
            }
        }
    }

    private bool Evaluate(object? subject, object? condition, bool canNarrowField)
    {
        if (condition is Condition wrapped) condition = wrapped.ToMap();

        // A bare value is shorthand for $eq; an explicit null means "equals null", not a wildcard.
        if (condition is null or string or bool || Equality.IsNumber(condition))
        {
            return Equality.Eq(subject, condition);
        }

        if (condition is not IDictionary<string, object?> map) return false;

        // The spec requires every key to be evaluated and ANDed together - no
        // key may "consume" the whole object or cause sibling keys to be ignored.
        foreach (var (key, value) in map)
        {
            if (!EvaluateKey(subject, key, value, canNarrowField)) return false;
        }

        return true;
    }

    private bool EvaluateKey(object? subject, string key, object? value, bool canNarrowField)
    {
        var ctx = canNarrowField ? _topContext : _nestedContext;

        if (!key.StartsWith('$')) return FieldAccess.Check(subject, key, value, ctx);

        var resolver = _registry.Get(key);
        return resolver is not null && resolver(subject, value, ctx);
    }

    private sealed class Context(ConditionResolver owner, bool canNarrowField) : IOperatorContext
    {
        public bool CanNarrowField => canNarrowField;

        public bool ResolveSubcondition(object? subject, object? condition) =>
            owner.Evaluate(subject, condition, canNarrowField);

        public bool ResolveFieldSubcondition(object? subject, object? condition) =>
            owner.Evaluate(subject, condition, canNarrowField: false);

        public void ReportTypeIssue(string operatorName, string message) =>
            Diagnostics.LogTypeIssue(owner._logger, operatorName, message);
    }
}
