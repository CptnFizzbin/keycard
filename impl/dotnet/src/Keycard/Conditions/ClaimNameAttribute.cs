namespace Keycard.Conditions;

/// <summary>
/// Names a claims property or field as a policy sees it, when that differs from
/// the camelCased member name. Conditions built with
/// <see cref="Condition{TClaims}"/> write this name, and field conditions
/// match it when reading claims.
/// </summary>
/// <example>
/// <code>
/// public sealed record Claims([property: ClaimName("owner_id")] int OwnerId);
/// </code>
/// </example>
[AttributeUsage(AttributeTargets.Property | AttributeTargets.Field)]
public sealed class ClaimNameAttribute(string name) : Attribute
{
    public string Name { get; } = name;
}
