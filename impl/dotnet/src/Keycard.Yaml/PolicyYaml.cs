using System.Collections;
using System.Globalization;
using Keycard.Errors;
using YamlDotNet.Core;
using YamlDotNet.RepresentationModel;

namespace Keycard.Yaml;

/// <summary>Reads and writes <see cref="PolicyDefinition"/> documents as YAML.</summary>
/// <example>
/// <code>
/// var definition = PolicyYaml.Parse(text);
/// string text = PolicyYaml.Serialize(definition);
/// </code>
/// </example>
public static class PolicyYaml
{
    /// <summary>Parses a YAML policy document, which must be a single YAML document.</summary>
    /// <exception cref="PolicyLoadException">The text isn't valid YAML, or isn't a valid policy document.</exception>
    public static PolicyDefinition Parse(string yaml)
    {
        ArgumentNullException.ThrowIfNull(yaml);

        var stream = new YamlStream();
        try
        {
            stream.Load(new StringReader(yaml));
        }
        catch (YamlException e)
        {
            throw new PolicyLoadException($"Invalid policy document: {e.Message}", e);
        }

        if (stream.Documents.Count != 1)
        {
            throw new PolicyLoadException($"A policy file must hold exactly one YAML document, found {stream.Documents.Count}.");
        }

        return PolicyDefinition.FromData(ToData(stream.Documents[0].RootNode));
    }

    /// <summary>Writes <paramref name="definition"/> as a YAML document.</summary>
    public static string Serialize(PolicyDefinition definition)
    {
        ArgumentNullException.ThrowIfNull(definition);

        var stream = new YamlStream(new YamlDocument(ToNode(definition.ToData())));
        using var writer = new StringWriter();
        stream.Save(writer, assignAnchors: false);
        return writer.ToString();
    }

    private static object? ToData(YamlNode node) => node switch
    {
        YamlMappingNode map => map.Children.ToDictionary(
            kv => (kv.Key as YamlScalarNode)?.Value ?? throw new PolicyLoadException("YAML map keys must be strings."),
            kv => ToData(kv.Value)),
        YamlSequenceNode seq => seq.Children.Select(ToData).ToList(),
        YamlScalarNode scalar => Scalar(scalar),
        _ => throw new PolicyLoadException($"Unsupported YAML node {node.GetType().Name}."),
    };

    // Only a plain (unquoted) scalar is typed; a quoted one is always a string.
    // YAML 1.2 core schema, so "on" and "no" stay strings.
    private static object? Scalar(YamlScalarNode scalar)
    {
        var text = scalar.Value;
        if (scalar.Style != ScalarStyle.Plain || text is null) return text;

        switch (text)
        {
            case "null" or "Null" or "NULL" or "~" or "":
                return null;
            case "true" or "True" or "TRUE":
                return true;
            case "false" or "False" or "FALSE":
                return false;
            case ".nan" or ".NaN" or ".NAN":
                return double.NaN;
            case ".inf" or ".Inf" or ".INF":
                return double.PositiveInfinity;
            case "-.inf" or "-.Inf" or "-.INF":
                return double.NegativeInfinity;
        }

        if (long.TryParse(text, NumberStyles.AllowLeadingSign, CultureInfo.InvariantCulture, out var integer)) return integer;
        if (double.TryParse(text, NumberStyles.Float, CultureInfo.InvariantCulture, out var number)) return number;
        return text;
    }

    private static YamlNode ToNode(object? value)
    {
        switch (value)
        {
            case null:
                return new YamlScalarNode("null");
            case string s:
                // Quoted, so a string like "1" or "true" is read back as a string.
                return new YamlScalarNode(s) { Style = ScalarStyle.DoubleQuoted };
            case bool b:
                return new YamlScalarNode(b ? "true" : "false");
            case IReadOnlyDictionary<string, object?> map:
            {
                var node = new YamlMappingNode();
                foreach (var (key, item) in map) node.Add(Key(key), ToNode(item));
                return node;
            }
            case IDictionary map:
            {
                var node = new YamlMappingNode();
                foreach (DictionaryEntry entry in map) node.Add(Key(Convert.ToString(entry.Key)!), ToNode(entry.Value));
                return node;
            }
            case IEnumerable sequence:
            {
                var node = new YamlSequenceNode();
                foreach (var item in sequence) node.Add(ToNode(item));
                return node;
            }
            default:
                return new YamlScalarNode(Convert.ToString(value, CultureInfo.InvariantCulture));
        }
    }

    private static YamlScalarNode Key(string key) => new(key) { Style = ScalarStyle.DoubleQuoted };
}
