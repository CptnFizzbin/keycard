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

```csharp
using Keycard;
using Keycard.Conditions;

var policy = new PolicyBuilder()
    .Allow("Read", "Article")
    .Allow("Update", "Article", Condition.Eq("owner_id", 1))
    .Deny("Delete", "Article", Condition.Eq("status", "archived"))
    .Build();

policy.Can("Read", "Article");                                          // true
policy.Can("Update", Subject.Of("Article", new { owner_id = 1 }));      // true
policy.Can("Update", Subject.Of("Article", new { owner_id = 2 }));      // false
policy.Can("Update", "Article");                                        // false: a conditional rule needs an instance
policy.Require("Delete", Subject.Of("Article", new { status = "archived" })); // throws PolicyException
```

The last rule that matches an action, subject, and condition decides; no match
means deny. Add general rules first and the rules that override them later.

## Loading a policy document

A policy is a plain, JSON-encodable `PolicyDefinition`, so it can be built
server-side and evaluated anywhere.

```csharp
var json = new PolicyBuilder().Allow("Read", "Article").BuildDef().ToJson();

var policy = new Policy(PolicyDefinition.FromJson(json));
```

KeyCard reads and writes JSON only. To load a YAML document, parse it with the
YAML library of your choice into the same shape first.

## Subject claims

Field conditions read a subject's claims from a map (`IDictionary`) or from the
public properties and fields of any object. A condition field matches a
property by its exact name, its `[JsonPropertyName]`, or its camelCased name, so
`{ ownerId: 1 }` reads `OwnerId` on a C# record.

`Subject<TClaims>` gives a subject a claims type to check at compile time:

```csharp
record ArticleClaims(int OwnerId, string Status);

var Article = new Subject<ArticleClaims>("Article");
policy.Can("Update", Article.Wrap(new ArticleClaims(1, "open")));
```

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
    .Allow("Delete", "Article", Condition.Op("$hasRole", "admin"))
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
