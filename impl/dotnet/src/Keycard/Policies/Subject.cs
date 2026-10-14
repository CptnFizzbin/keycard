namespace Keycard;

/// <summary>
/// The thing a user wants to act on: a name, optionally wrapping the claims of
/// one instance for a rule's conditions to inspect. A subject with no claims
/// is a bare type check and never satisfies a conditional rule.
/// </summary>
/// <example>
/// <code>
/// policy.Can("Update", "Article");                                   // bare type
/// policy.Can("Update", Subject.Of("Article").Wrap(new { owner_id = 1 })); // one instance
/// </code>
/// </example>
public class Subject
{
    protected Subject(string name, object? claims)
    {
        Name = name ?? throw new ArgumentNullException(nameof(name));
        Claims = claims;
    }

    public string Name { get; }

    /// <summary>
    /// The wrapped instance's claims, or <c>null</c> for a bare type. Maps
    /// (<see cref="System.Collections.IDictionary"/>) and objects with public
    /// properties or fields are both readable by field conditions.
    /// </summary>
    public object? Claims { get; }

    public bool HasClaims => Claims is not null;

    public static Subject Of(string name) => new(name, null);

    public static Subject Of(string name, object claims) =>
        new(name, claims ?? throw new ArgumentNullException(nameof(claims)));

    /// <summary>Returns a copy of this subject wrapping <paramref name="claims"/>.</summary>
    public Subject Wrap(object claims) => Of(Name, claims);

    public static implicit operator Subject(string name) => Of(name);

    public override string ToString() => Name;
}

/// <summary>A <see cref="Subject"/> whose claims have a known shape, so wrapping checks the type at compile time.</summary>
/// <typeparam name="TClaims">The shape of this subject's claims.</typeparam>
public sealed class Subject<TClaims> : Subject where TClaims : notnull
{
    public Subject(string name) : base(name, null) { }

    private Subject(string name, TClaims claims) : base(name, claims) { }

    /// <summary>Returns a copy of this subject wrapping <paramref name="claims"/>.</summary>
    public Subject<TClaims> Wrap(TClaims claims) =>
        new(Name, claims ?? throw new ArgumentNullException(nameof(claims)));
}
