using Keycard.Errors;

namespace Keycard;

/// <summary>
/// A declared <c>meta.anyAction</c> or <c>meta.anySubject</c>: either disabled
/// or a named token. An undeclared position is represented by <c>null</c>
/// rather than a <see cref="WildcardToken"/>, and defaults to <c>"_ANY_"</c>.
/// </summary>
public abstract record WildcardToken
{
    /// <summary>The token an undeclared wildcard position defaults to.</summary>
    public const string DefaultToken = "_ANY_";

    /// <summary>The wildcard mechanism is disabled: no string, including <c>"_ANY_"</c>, has special meaning.</summary>
    public sealed record Disabled : WildcardToken;

    /// <summary>The wildcard mechanism uses <see cref="Token"/>.</summary>
    public sealed record Named(string Token) : WildcardToken;

    /// <summary>The shared <see cref="Disabled"/> instance.</summary>
    public static readonly WildcardToken Off = new Disabled();

    /// <summary>Returns the token an undeclared position resolves to.</summary>
    public static WildcardToken OrDefault(WildcardToken? declared) => declared ?? new Named(DefaultToken);

    /// <summary>
    /// Parses a raw declaration: <c>null</c> or <c>false</c> disables the
    /// wildcard and a string names a token.
    /// </summary>
    /// <exception cref="PolicyLoadException">Any other value, which is never coerced.</exception>
    public static WildcardToken Of(object? raw) => raw switch
    {
        null => Off,
        false => Off,
        true => throw new PolicyLoadException(
            "meta.anyAction/meta.anySubject: \"true\" is not a valid declaration - use a string token to name"
            + " a wildcard, or null/false to disable it."),
        string token => new Named(token),
        _ => throw new PolicyLoadException(
            $"meta.anyAction/meta.anySubject: expected a string, null, or false, got {raw.GetType().Name}."),
    };

    /// <summary>Returns whether <paramref name="value"/> is this position's wildcard token.</summary>
    public static bool IsWildcard(WildcardToken any, string value) => any is Named named && named.Token == value;

    /// <summary>Returns whether a rule's <paramref name="ruleValue"/> applies to <paramref name="value"/>, exactly or through <paramref name="any"/>.</summary>
    public static bool Matches(string value, string ruleValue, WildcardToken any) =>
        value == ruleValue || IsWildcard(any, ruleValue);
}
