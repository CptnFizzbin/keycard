# CptnFizzbin.Keycard.Xml

Reads and writes [KeyCard](https://github.com/cptnfizzbin/keycard) policy documents as XML,
on top of [`CptnFizzbin.Keycard`](../../README.md) (which itself does no serialization).

```bash
dotnet add package CptnFizzbin.Keycard.Xml
```

```csharp
using Keycard.Xml;

PolicyDefinition definition = PolicyXml.Parse(text);            // string -> definition
string text = PolicyXml.Serialize(definition, indented: true);  // definition -> string

XElement element = PolicyXml.Write(definition);                 // definition -> <policy> element
definition = PolicyXml.Read(element);                           // <policy> element -> definition

var policy = new Policy(definition);
```

A malformed document, or text that isn't valid XML, throws `PolicyLoadException`.

## Document format

Plain XML text can't tell `"1"` from `1` from `true` from `null`, so values are typed elements:
`<string>`, `<number>`, `<bool>`, `<null/>`, `<list>` of values, and `<map>` of `<item key="...">`
entries. The root is `<policy>` and holds the document's `<item>` entries directly:

```xml
<policy>
  <item key="version"><string>0.1</string></item>
  <item key="rules">
    <list>
      <list><string>allow</string><string>Read</string><string>Article</string></list>
      <list>
        <string>allow</string><string>Update</string><string>Article</string>
        <map><item key="owner_id"><number>1</number></item></map>
      </list>
    </list>
  </item>
</policy>
```
