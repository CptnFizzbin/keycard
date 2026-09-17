# KeyCard

> ⚠️ **Pre-alpha.** The API, the policy format, and the condition language are
> all still subject to breaking changes without notice. Nothing here is
> recommended for production use yet.

**KeyCard** is a cross-language access-control library, strongly inspired by
[CASL.js](https://casl.js.org/). It lets you **define** an authorization
policy once — in one place, in one format — and **enforce** it anywhere:
server-side, client-side, in a different process, or in a completely
different programming language.

- **Define** a policy once with a fluent, type-safe `PolicyBuilder` API.
- **Encode** it as a small, order-significant, JSON-encodable
  `PolicyDefinition`.
- **Enforce** it anywhere — ship the same `PolicyDefinition` to a browser, a
  mobile client, or a service written in another language, and every one of
  them evaluates the exact same rules the exact same way.

```yaml
version: "1.0" # KeyCard policy spec version (SemVer)
meta:
  actions: [ Create, Update, Delete ]
  subjects: [ Article ]
rules:
  - [ allow, Create, Article ]                                     # anyone may create an article
  - [ allow, Update, Article, { owner_id: 1 } ]                     # only the owner may update
  - [ deny, Delete, Article, { status: { $not: "archived" } } ]     # can't delete unless archived
```

## Documentation

The full guide, language-specific quick starts, and API references live on
the **[KeyCard website](https://keycard.cptnfizzbin.dev)**:

- [Guide](https://keycard.cptnfizzbin.dev/docs/intro) — the language-agnostic
  policy format, condition language, and evaluation rules
- [JavaScript](https://keycard.cptnfizzbin.dev/js/intro) — installation and
  API reference
- [Java](https://keycard.cptnfizzbin.dev/java/intro) — installation and API
  reference

For a terser, source-of-truth overview see [`SPEC.md`](SPEC.md) and
[`GLOSSARY.md`](GLOSSARY.md). The normative v1 specification —  exact
rule-evaluation semantics, the full condition-operator table, and required
edge-case behavior every implementation is validated against — lives at
[`docs/spec/SPEC_V1-0.md`](docs/spec/SPEC_V1-0.md).

## Implementations

Each language implementation lives under `impl/` and is released
independently on its own version number.

| Language   | Package                                                          | Source        |
|------------|-------------------------------------------------------------------|---------------|
| JavaScript | [`@cptn-fizzbin/keycard`](https://www.npmjs.com/package/@cptn-fizzbin/keycard) (npm) | [`impl/js`](impl/js)     |
| Java       | `com.cptnfizzbin:keycard` (Maven Central)                          | [`impl/java`](impl/java) |

See [`RELEASING.md`](RELEASING.md) for release mechanics, including how to
add a new language implementation.

## Repository layout

```
impl/       Language implementations (one package per language)
docs/       Normative specification (docs/spec/SPEC_V1-0.md)
website/    Docusaurus source for keycard.cptnfizzbin.dev
test/       Cross-language conformance fixtures
```

This is a Yarn workspaces monorepo. To work on the website locally:

```bash
yarn install
yarn start:website
```

## License

[MIT](LICENSE)
