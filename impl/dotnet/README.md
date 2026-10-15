# KeyCard for .NET

The C# implementation of [KeyCard](../../README.md): one policy format
([`SPEC.md`](../../SPEC.md), normative spec in
[`SPEC_V0.md`](../../website/spec/SPEC_V0.md)), evaluated the same way in every
language. It targets `net8.0` and passes the shared conformance fixtures under
[`test/fixtures`](../../test/fixtures).

```bash
dotnet add package CptnFizzbin.Keycard
```

## Quickstart

Declare each kind of subject once, as a subclass of `Subject<TClaims>` that maps
your domain entity to the claims a policy's conditions can see:

```csharp
using Keycard;
using Keycard.Conditions;

public sealed record ArticleClaims(int OwnerId, string Status);

public sealed class ArticleSubject : Subject<ArticleClaims>
{
    public static readonly ArticleSubject Def = new();   // the bare type, for rules

    private ArticleSubject() : base("Article") { }
    private ArticleSubject(ArticleClaims claims) : base("Article", claims) { }

    public static ArticleSubject Of(Article article) =>
        new(new ArticleClaims(article.OwnerId, article.Status));
}

// Actions are plain names; declare your vocabulary once.
public static class Action
{
    public static readonly PolicyAction Read = new("Read");
    public static readonly PolicyAction Update = new("Update");
    public static readonly PolicyAction Delete = new("Delete");
}
```

Then build a policy and check it:

```csharp
var policy = new PolicyBuilder()
    .Allow(Action.Read, ArticleSubject.Def)
    .Allow(Action.Update, ArticleSubject.Def, Condition<ArticleClaims>.Eq(c => c.OwnerId, currentUser.Id))
    .Deny(Action.Delete, ArticleSubject.Def, Condition<ArticleClaims>.Eq(c => c.Status, "archived"))
    .Build();

policy.Can(Action.Update, ArticleSubject.Of(article));   // true when the article is theirs
policy.Can(Action.Update, ArticleSubject.Def);           // false: a conditional rule needs an instance
policy.Require(Action.Delete, ArticleSubject.Of(article)); // throws PolicyException when denied
```

The last rule that matches an action, subject, and condition decides; no match
means deny. Add general rules first and the rules that override them later.

## Type-safe conditions

`Condition<TClaims>` picks fields with a lambda and checks values against the
field's type, and `PolicyBuilder` only accepts it alongside a `Subject<TClaims>`
with the same claims type. A misspelled field, a mistyped value, or a condition
for another subject is a compile error.

```csharp
Condition<ArticleClaims>.Or(
    Condition<ArticleClaims>.Eq(c => c.OwnerId, user.Id),
    Condition<ArticleClaims>.In(c => c.Status, "draft", "published"));

Condition<ArticleClaims>.Eq(c => c.OwnerId, "1");   // compile error: OwnerId is an int
```

A field is written into the policy under its `[ClaimName]`, or else its
camelCased name (`OwnerId` becomes `ownerId`), which is the spelling the other
implementations use. Plain strings still convert to actions and subjects, and
the untyped `Condition` (`Condition.Eq("owner_id", 1)`) remains for conditions
assembled dynamically.

## Policy documents and serialization

A `PolicyDefinition` is a plain object model that can be built server-side and
evaluated anywhere. KeyCard converts it to and from a `System.Text.Json.Nodes`
`JsonObject`, and leaves everything else about serialization to the application:
parsing text, writing text, and any other format.

```csharp
// Server: build, then hand the application a JsonObject to send however it likes.
JsonObject node = new PolicyBuilder().Allow(Action.Read, ArticleSubject.Def).BuildDef().ToJsonObject();
string text = node.ToJsonString();

// Client: parse, then construct.
var policy = new Policy(PolicyDefinition.FromJsonObject((JsonObject)JsonNode.Parse(text)!));
```

`FromJsonObject` throws `PolicyLoadException` for a malformed document and does
not modify the object it is given. For YAML or another format, build the
`PolicyDefinition` yourself: a rule is `new Rule(effect, action, subject, conditions)`,
`meta.anyAction`/`meta.anySubject` distinguish absent (leave `null`) from an explicit
`null` or `false` (`WildcardToken.Off`; `WildcardToken.Of(raw)` maps a parsed value),
and conditions are plain data (dictionaries, lists, strings, numbers, booleans, `null`).

## Subject claims

Field conditions read a subject's claims from a map (`IDictionary`) or from the
public properties and fields of any object. A condition field matches a
property by its exact name, its `[ClaimName]`, or its camelCased name, so
`{ ownerId: 1 }` reads `OwnerId` on a C# record.

## Custom operators

A policy can use `$`-prefixed operators the host supplies. List them in
`meta.operators` (the builder does this automatically) and register their
behavior; a `Policy` refuses to load if a declared operator isn't registered.

```csharp
var config = new KeycardConfig
{
    Operators = new OperatorCatalog().Add("$hasRole", (subject, role) =>
        subject is IDictionary<string, object?> map
        && map.TryGetValue("roles", out var roles)
        && roles is IEnumerable<object?> list
        && list.Contains(role)),
};

var policy = new PolicyBuilder(config)
    .Allow(Action.Delete, ArticleSubject.Def, Condition<ArticleClaims>.Op("$hasRole", "admin"))
    .Build();
```

## Diagnostics

A condition that can't be meaningfully evaluated (a type mismatch, a malformed
operand) is `false`. Set `KeycardConfig.Logger` to an `ILogger` to have these
reported at `Error` level; by default they are discarded.

## Development

```bash
dotnet test tests/Keycard.Tests
```
