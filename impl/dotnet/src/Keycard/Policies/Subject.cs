namespace Keycard;

/// <summary>
/// The thing a user wants to act on: a name, optionally wrapping the claims of
/// one instance for a rule's conditions to inspect. A subject with no claims
/// is a bare type check and never satisfies a conditional rule.
/// </summary>
/// <remarks>
/// Applications normally derive from <see cref="Subject{TClaims}"/> rather than
/// use this type directly.
/// </remarks>
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

    /// <summary>A bare type check with no instance.</summary>
    public static Subject Named(string name) => new(name, null);

    /// <summary>A check against one instance whose claims are <paramref name="claims"/>.</summary>
    public static Subject Named(string name, object claims) =>
        new(name, claims ?? throw new ArgumentNullException(nameof(claims)));

    public static implicit operator Subject(string name) => Named(name);

    public override string ToString() => Name;
}

/// <summary>
/// A <see cref="Subject"/> whose claims have a known shape. Derive from it to
/// give each kind of subject its own name and a factory that maps a domain
/// entity to claims, so call sites read <c>policy.Can(Action.Update, ArticleSubject.Of(article))</c>
/// and conditions on it are checked against <typeparamref name="TClaims"/>.
/// </summary>
/// <example>
/// <code>
/// public sealed record ArticleClaims(int OwnerId, string Status);
///
/// public sealed class ArticleSubject : Subject&lt;ArticleClaims&gt;
/// {
///     public static readonly ArticleSubject Def = new();
///
///     private ArticleSubject() : base("Article") { }
///
///     private ArticleSubject(ArticleClaims claims) : base("Article", claims) { }
///
///     public static ArticleSubject Of(Article article) => new(new ArticleClaims(article.OwnerId, article.Status));
/// }
/// </code>
/// </example>
/// <typeparam name="TClaims">The shape of this subject's claims.</typeparam>
public class Subject<TClaims> : Subject where TClaims : class
{
    /// <summary>Creates the bare type: a subject with a name and no instance.</summary>
    public Subject(string name) : base(name, null) { }

    /// <summary>Creates a subject wrapping one instance's claims.</summary>
    protected Subject(string name, TClaims claims)
        : base(name, claims ?? throw new ArgumentNullException(nameof(claims))) { }

    public new TClaims? Claims => (TClaims?)base.Claims;

    /// <summary>Returns a copy of this subject wrapping <paramref name="claims"/>.</summary>
    public Subject<TClaims> Wrap(TClaims claims) => new(Name, claims);
}
