# CptnFizzbin.Keycard.Json

Reads and writes [KeyCard](https://github.com/cptnfizzbin/keycard) policy documents as JSON,
on top of [`CptnFizzbin.Keycard`](../../README.md) (which itself does no serialization).

```bash
dotnet add package CptnFizzbin.Keycard.Json
```

```csharp
using Keycard.Json;

PolicyDefinition definition = PolicyJson.Parse(text);          // string -> definition
string text = PolicyJson.Serialize(definition, indented: true); // definition -> string

JsonObject node = PolicyJson.Write(definition);                 // definition -> JsonObject
definition = PolicyJson.Read(node);                             // JsonObject -> definition (node is not modified)

var policy = new Policy(definition);
```

A malformed document, or text that isn't valid JSON, throws `PolicyLoadException`.

## As a `JsonConverter`

To have `JsonSerializer` read and write a `PolicyDefinition` nested in your own types, register
`PolicyDefinitionJsonConverter`:

```csharp
var options = new JsonSerializerOptions { Converters = { new PolicyDefinitionJsonConverter() } };
var envelope = JsonSerializer.Deserialize<Envelope>(text, options); // Envelope has a PolicyDefinition property
```
