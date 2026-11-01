namespace Keycard;

/// <summary>
/// The name of something a user wants to do (<c>Read</c>, <c>Update</c>, ...).
/// A plain string converts to one implicitly; an application usually declares
/// its own vocabulary once and refers to it everywhere.
/// </summary>
/// <example>
/// <code>
/// static class Action
/// {
///     public static readonly PolicyAction Read = new("Read");
///     public static readonly PolicyAction Update = new("Update");
/// }
///
/// policy.Can(Action.Update, ArticleSubject.Of(article));
/// </code>
/// </example>
public readonly record struct PolicyAction(string Name)
{
    public static implicit operator PolicyAction(string name) => new(name);

    public static implicit operator string(PolicyAction action) => action.Name;

    public override string ToString() => Name;
}
