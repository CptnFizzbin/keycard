using Keycard.Errors;

namespace Keycard.Conditions;

/// <summary>
/// Every operator a <see cref="ConditionResolver"/> can dispatch to: the
/// built-ins, registered on construction, plus any custom ones added after.
/// Built-ins can't be removed or replaced.
/// </summary>
/// <example>
/// <code>
/// var operators = new OperatorCatalog()
///     .Add("$hasRole", (subject, value) => subject is IEnumerable&lt;string&gt; roles &amp;&amp; roles.Contains(value as string));
/// </code>
/// </example>
public sealed class OperatorCatalog
{
    /// <summary>The names of every built-in operator.</summary>
    public static readonly IReadOnlySet<string> BuiltinNames = DefaultOperators.Names;

    private readonly Dictionary<string, OperatorResolver> _operators = new();
    private readonly List<string> _order = new();

    public OperatorCatalog()
    {
        foreach (var (name, resolver) in DefaultOperators.All) Register(name, resolver);
    }

    /// <summary>
    /// Registers <paramref name="resolver"/> under <paramref name="name"/>.
    /// </summary>
    /// <exception cref="PolicyLoadException">
    /// The name isn't <c>"$"</c> followed by at least one character, or an
    /// operator with that name is already registered, built-in or custom.
    /// </exception>
    public OperatorCatalog Add(string name, OperatorResolver resolver)
    {
        if (name is null || !name.StartsWith('$') || name.Length < 2)
        {
            throw new PolicyLoadException(
                $"Invalid operator name \"{name}\": operator names MUST start with \"$\" (e.g. \"$hasRole\").");
        }

        if (_operators.ContainsKey(name))
        {
            throw new PolicyLoadException(
                $"Duplicate operator \"{name}\": an operator with this name is already registered"
                + " (built-in or custom) - operator names MUST be unique.");
        }

        Register(name, resolver);
        return this;
    }

    /// <summary>Registers a flat operator that never needs to evaluate nested conditions.</summary>
    public OperatorCatalog Add(string name, Func<object?, object?, bool> resolver) =>
        Add(name, (subject, value, _) => resolver(subject, value));

    /// <summary>The resolver registered under <paramref name="name"/>, or <c>null</c>.</summary>
    public OperatorResolver? Get(string name) => _operators.GetValueOrDefault(name);

    public bool Contains(string name) => _operators.ContainsKey(name);

    /// <summary>Every registered operator name, built-ins first, in registration order.</summary>
    public IReadOnlyList<string> Names => _order;

    /// <summary>Every registered operator name that isn't a built-in, in registration order.</summary>
    public IReadOnlyList<string> CustomNames => _order.Where(n => !BuiltinNames.Contains(n)).ToList();

    private void Register(string name, OperatorResolver resolver)
    {
        _operators[name] = resolver;
        _order.Add(name);
    }
}
