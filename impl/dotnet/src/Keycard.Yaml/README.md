# CptnFizzbin.Keycard.Yaml

Reads and writes [KeyCard](https://github.com/cptnfizzbin/keycard) policy documents as YAML
(using [YamlDotNet](https://github.com/aaubry/YamlDotNet)), on top of
[`CptnFizzbin.Keycard`](../../README.md) (which itself does no serialization).

```bash
dotnet add package CptnFizzbin.Keycard.Yaml
```

```csharp
using Keycard.Yaml;

PolicyDefinition definition = PolicyYaml.Parse(text);   // string -> definition
string text = PolicyYaml.Serialize(definition);         // definition -> string

var policy = new Policy(definition);
```

```yaml
version: "0.1"
meta:
  actions: [Create, Update, Delete]
  subjects: [Article]
rules:
  - [allow, Create, Article]
  - [allow, Update, Article, { owner_id: 1 }]
  - [deny, Delete, Article, { status: "archived" }]
```

A malformed document, text that isn't valid YAML, or a file holding more than one YAML document
throws `PolicyLoadException`. Only unquoted scalars are typed (`1` is a number, `"1"` is a string,
and `no` stays a string); `Serialize` quotes every string so it reads back unchanged.
