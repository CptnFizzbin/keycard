using System.Collections;
using System.Globalization;
using System.Xml.Linq;

namespace Keycard.Tests;

/// <summary>
/// A small typed XML encoding of plain data, so a policy document can travel as
/// XML without losing the difference between <c>"1"</c>, <c>1</c>, <c>true</c>,
/// and <c>null</c>: <c>&lt;map&gt;</c> holds <c>&lt;item key="..."&gt;</c> entries,
/// <c>&lt;list&gt;</c> holds values, and scalars are <c>&lt;string&gt;</c>,
/// <c>&lt;number&gt;</c>, <c>&lt;bool&gt;</c>, and <c>&lt;null/&gt;</c>.
/// </summary>
internal static class XmlPlain
{
    public static string Dump(object? value) => new XDocument(Encode(value)).ToString(SaveOptions.DisableFormatting);

    public static object? Load(string xml) => Decode(XDocument.Parse(xml, LoadOptions.PreserveWhitespace).Root!);

    private static XElement Encode(object? value) => value switch
    {
        null => new XElement("null"),
        string s => new XElement("string", s),
        bool b => new XElement("bool", b ? "true" : "false"),
        IDictionary map => new XElement("map", Entries(map)
            .Select(e => new XElement("item", new XAttribute("key", (string)e.Key), Encode(e.Value)))),
        IEnumerable sequence => new XElement("list", sequence.Cast<object?>().Select(Encode)),
        _ => new XElement("number", Convert.ToString(value, CultureInfo.InvariantCulture)),
    };

    // Not Cast<DictionaryEntry>: a generic Dictionary's IEnumerable yields KeyValuePairs.
    private static IEnumerable<DictionaryEntry> Entries(IDictionary map)
    {
        foreach (DictionaryEntry entry in map) yield return entry;
    }

    private static object? Decode(XElement element) => element.Name.LocalName switch
    {
        "null" => null,
        "string" => element.Value,
        "bool" => element.Value == "true",
        "number" => long.TryParse(element.Value, NumberStyles.AllowLeadingSign, CultureInfo.InvariantCulture, out var integer)
            ? integer
            : double.Parse(element.Value, NumberStyles.Float, CultureInfo.InvariantCulture),
        "map" => element.Elements("item").ToDictionary(i => (string)i.Attribute("key")!, i => Decode(i.Elements().Single())),
        "list" => element.Elements().Select(Decode).ToList(),
        var other => throw new FormatException($"Unexpected element <{other}>."),
    };
}
