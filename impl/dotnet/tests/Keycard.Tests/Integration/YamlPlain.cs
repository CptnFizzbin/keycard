using System.Globalization;
using YamlDotNet.Core;
using YamlDotNet.RepresentationModel;

namespace Keycard.Tests.Integration;

/// <summary>
/// Reads YAML into plain CLR data (maps, lists, strings, longs, doubles,
/// bools, null) the way a JSON document would be read, so fixtures can be
/// handed to the library exactly as a parsed policy document would be.
/// </summary>
internal static class YamlPlain
{
    public static List<object?> LoadDocuments(string path) => Parse(File.ReadAllText(path));

    public static List<object?> Parse(string yaml)
    {
        var stream = new YamlStream();
        stream.Load(new StringReader(yaml));
        return stream.Documents.Select(d => Convert(d.RootNode)).ToList();
    }

    private static object? Convert(YamlNode node) => node switch
    {
        YamlMappingNode map => map.Children.ToDictionary(
            kv => ((YamlScalarNode)kv.Key).Value ?? "",
            kv => Convert(kv.Value)),
        YamlSequenceNode seq => seq.Children.Select(Convert).ToList(),
        YamlScalarNode scalar => Scalar(scalar),
        _ => throw new InvalidOperationException($"Unsupported YAML node {node.GetType().Name}"),
    };

    // Only a plain (unquoted) scalar is typed; a quoted one is always a string.
    // YAML 1.2 core schema, so "on"/"no" stay strings.
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
}
