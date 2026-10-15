using System.Collections;
using System.Globalization;
using System.Xml;
using System.Xml.Linq;
using Keycard.Errors;

namespace Keycard.Xml;

/// <summary>
/// Reads and writes <see cref="PolicyDefinition"/> documents as XML. Plain XML
/// text can't tell <c>"1"</c> from <c>1</c> from <c>true</c> from <c>null</c>,
/// so values are typed elements: <c>&lt;string&gt;</c>, <c>&lt;number&gt;</c>,
/// <c>&lt;bool&gt;</c>, <c>&lt;null/&gt;</c>, <c>&lt;list&gt;</c> of values, and
/// <c>&lt;map&gt;</c> of <c>&lt;item key="..."&gt;</c> entries. The root element is
/// <c>&lt;policy&gt;</c> and holds the document's <c>&lt;item&gt;</c> entries.
/// </summary>
/// <example>
/// <code>
/// &lt;policy&gt;
///   &lt;item key="version"&gt;&lt;string&gt;0.1&lt;/string&gt;&lt;/item&gt;
///   &lt;item key="rules"&gt;
///     &lt;list&gt;
///       &lt;list&gt;&lt;string&gt;allow&lt;/string&gt;&lt;string&gt;Read&lt;/string&gt;&lt;string&gt;Article&lt;/string&gt;&lt;/list&gt;
///     &lt;/list&gt;
///   &lt;/item&gt;
/// &lt;/policy&gt;
/// </code>
/// </example>
public static class PolicyXml
{
    private const string RootName = "policy";

    /// <summary>Parses an XML policy document.</summary>
    /// <exception cref="PolicyLoadException">The text isn't valid XML, or isn't a valid policy document.</exception>
    public static PolicyDefinition Parse(string xml)
    {
        ArgumentNullException.ThrowIfNull(xml);

        try
        {
            return Read(XDocument.Parse(xml, LoadOptions.PreserveWhitespace).Root!);
        }
        catch (XmlException e)
        {
            throw new PolicyLoadException($"Invalid policy document: {e.Message}", e);
        }
    }

    /// <summary>Reads a definition from a <c>&lt;policy&gt;</c> element, which is not modified.</summary>
    /// <exception cref="PolicyLoadException">The document is malformed.</exception>
    public static PolicyDefinition Read(XElement document)
    {
        ArgumentNullException.ThrowIfNull(document);

        if (document.Name.LocalName != RootName)
        {
            throw new PolicyLoadException($"A policy document's root element must be <{RootName}>, got <{document.Name.LocalName}>.");
        }

        return PolicyDefinition.FromData(DecodeItems(document));
    }

    /// <summary>Returns <paramref name="definition"/> as a new <c>&lt;policy&gt;</c> element.</summary>
    public static XElement Write(PolicyDefinition definition)
    {
        ArgumentNullException.ThrowIfNull(definition);
        return new XElement(RootName, EncodeItems(definition.ToData()));
    }

    /// <summary>Writes <paramref name="definition"/> as XML text.</summary>
    public static string Serialize(PolicyDefinition definition, bool indented = false) =>
        new XDocument(Write(definition)).ToString(indented ? SaveOptions.None : SaveOptions.DisableFormatting);

    private static IEnumerable<XElement> EncodeItems(IEnumerable<KeyValuePair<string, object?>> entries) =>
        entries.Select(e => new XElement("item", new XAttribute("key", e.Key), Encode(e.Value)));

    private static XElement Encode(object? value) => value switch
    {
        null => new XElement("null"),
        string s => new XElement("string", s),
        bool b => new XElement("bool", b ? "true" : "false"),
        IReadOnlyDictionary<string, object?> map => new XElement("map", EncodeItems(map)),
        IDictionary map => new XElement("map", EncodeItems(Entries(map))),
        IEnumerable sequence => new XElement("list", sequence.Cast<object?>().Select(Encode)),
        _ => new XElement("number", Convert.ToString(value, CultureInfo.InvariantCulture)),
    };

    // Not Cast<DictionaryEntry>: a generic Dictionary's IEnumerable yields KeyValuePairs.
    private static IEnumerable<KeyValuePair<string, object?>> Entries(IDictionary map)
    {
        foreach (DictionaryEntry entry in map) yield return new(Convert.ToString(entry.Key)!, entry.Value);
    }

    private static Dictionary<string, object?> DecodeItems(XElement parent)
    {
        var map = new Dictionary<string, object?>();
        foreach (var item in parent.Elements())
        {
            if (item.Name.LocalName != "item" || item.Attribute("key")?.Value is not { } key)
            {
                throw new PolicyLoadException($"Expected <item key=\"...\"> inside <{parent.Name.LocalName}>, got <{item.Name.LocalName}>.");
            }

            var children = item.Elements().ToList();
            if (children.Count != 1)
            {
                throw new PolicyLoadException($"<item key=\"{key}\"> must hold exactly one value element, got {children.Count}.");
            }

            map[key] = Decode(children[0]);
        }

        return map;
    }

    private static object? Decode(XElement element)
    {
        switch (element.Name.LocalName)
        {
            case "null":
                return null;
            case "string":
                return element.Value;
            case "bool":
                return element.Value switch
                {
                    "true" => true,
                    "false" => false,
                    var other => throw new PolicyLoadException($"<bool> must be true or false, got \"{other}\"."),
                };
            case "number":
                if (long.TryParse(element.Value, NumberStyles.AllowLeadingSign, CultureInfo.InvariantCulture, out var integer)) return integer;
                if (double.TryParse(element.Value, NumberStyles.Float, CultureInfo.InvariantCulture, out var number)) return number;
                throw new PolicyLoadException($"<number> must hold a number, got \"{element.Value}\".");
            case "map":
                return DecodeItems(element);
            case "list":
                return element.Elements().Select(Decode).ToList();
            case var other:
                throw new PolicyLoadException($"Unexpected element <{other}>.");
        }
    }
}
