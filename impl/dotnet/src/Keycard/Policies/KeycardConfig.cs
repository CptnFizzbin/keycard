using Keycard.Conditions;
using Microsoft.Extensions.Logging;

namespace Keycard;

/// <summary>
/// Configuration shared by <see cref="PolicyBuilder"/> and <see cref="Policy"/>.
/// Both read it once, when constructed; changes made afterwards aren't seen by them.
/// </summary>
public sealed class KeycardConfig
{
    /// <summary>Where type-issue diagnostics from condition evaluation are reported (at <c>Error</c>); <c>null</c> discards them.</summary>
    public ILogger? Logger { get; set; }

    /// <summary>The built-in operators plus any custom ones the host registers.</summary>
    public OperatorCatalog Operators { get; set; } = new();

    /// <summary>Additional action names the policy's vocabulary allows, beyond those its rules use.</summary>
    public IList<string> Actions { get; } = new List<string>();

    /// <summary>Additional subject names the policy's vocabulary allows, beyond those its rules use.</summary>
    public IList<string> Subjects { get; } = new List<string>();

    /// <summary>
    /// When <c>true</c> (the default), a <see cref="Policy"/> rejects a definition
    /// that doesn't satisfy its own <c>meta.actions</c>, <c>meta.subjects</c>, and
    /// <c>meta.operators</c>, and <see cref="PolicyBuilder.BuildDef"/> attaches the
    /// derived catalogs. A configured wildcard is emitted as <c>meta.anyAction</c> /
    /// <c>meta.anySubject</c> either way, since evaluation depends on it. Structural
    /// rule checks (a malformed rule tuple, a conditional both-sides-wildcarded
    /// rule) run either way.
    /// </summary>
    public bool EmitMeta { get; set; } = true;

    /// <summary>The action wildcard token; <c>null</c> when never configured, so a built <c>meta.anyAction</c> stays undeclared.</summary>
    public WildcardToken? AnyAction { get; private set; }

    /// <summary>The subject wildcard token; <c>null</c> when never configured, so a built <c>meta.anySubject</c> stays undeclared.</summary>
    public WildcardToken? AnySubject { get; private set; }

    /// <summary>Declares <paramref name="token"/> as the action wildcard.</summary>
    public KeycardConfig UseAnyAction(string token)
    {
        AnyAction = new WildcardToken.Named(token ?? throw new ArgumentNullException(nameof(token)));
        return this;
    }

    /// <summary>Disables the action wildcard - no action name, including <c>"_ANY_"</c>, has special meaning.</summary>
    public KeycardConfig DisableAnyAction()
    {
        AnyAction = WildcardToken.Off;
        return this;
    }

    /// <summary>Declares <paramref name="token"/> as the subject wildcard.</summary>
    public KeycardConfig UseAnySubject(string token)
    {
        AnySubject = new WildcardToken.Named(token ?? throw new ArgumentNullException(nameof(token)));
        return this;
    }

    /// <summary>Disables the subject wildcard - no subject name, including <c>"_ANY_"</c>, has special meaning.</summary>
    public KeycardConfig DisableAnySubject()
    {
        AnySubject = WildcardToken.Off;
        return this;
    }
}
