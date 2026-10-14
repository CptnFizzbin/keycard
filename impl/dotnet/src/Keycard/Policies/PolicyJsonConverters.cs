using System.Text.Json;
using System.Text.Json.Serialization;
using Keycard.Conditions;
using Keycard.Errors;

namespace Keycard;

internal sealed class WildcardTokenConverter : JsonConverter<WildcardToken?>
{
    // Called for an explicit null (which disables the wildcard) but never for an
    // absent property, which keeps "declared null" distinct from "undeclared".
    public override bool HandleNull => true;

    public override WildcardToken? Read(ref Utf8JsonReader reader, Type typeToConvert, JsonSerializerOptions options) =>
        WildcardToken.Of(PlainJsonConverter.ReadValue(ref reader));

    public override void Write(Utf8JsonWriter writer, WildcardToken? value, JsonSerializerOptions options)
    {
        if (value is WildcardToken.Named named) writer.WriteStringValue(named.Token);
        else writer.WriteNullValue();
    }
}

internal sealed class RuleConverter : JsonConverter<Rule>
{
    public override Rule Read(ref Utf8JsonReader reader, Type typeToConvert, JsonSerializerOptions options)
    {
        if (reader.TokenType != JsonTokenType.StartArray)
        {
            throw new PolicyLoadException("Malformed rule tuple: a rule must be an array [effect, action, subject, condition?].");
        }

        var tuple = (List<object?>)PlainJsonConverter.ReadValue(ref reader)!;
        if (tuple.Count is < 3 or > 4)
        {
            throw new PolicyLoadException($"Malformed rule tuple: expected 3 or 4 elements, got {tuple.Count}.");
        }

        var effect = Text(tuple[0], "effect");
        var action = Text(tuple[1], "action");
        var subject = Text(tuple[2], "subject");

        IReadOnlyDictionary<string, object?>? conditions = null;
        if (tuple.Count == 4 && tuple[3] is not null)
        {
            conditions = tuple[3] as Dictionary<string, object?>
                ?? throw new PolicyLoadException("Malformed rule tuple: the condition must be an object.");
        }

        return new Rule(effect, action, subject, conditions);
    }

    public override void Write(Utf8JsonWriter writer, Rule value, JsonSerializerOptions options)
    {
        writer.WriteStartArray();
        writer.WriteStringValue(value.Effect);
        writer.WriteStringValue(value.Action);
        writer.WriteStringValue(value.Subject);
        if (value.Conditions is not null) PlainJsonConverter.WriteValue(writer, value.Conditions);
        writer.WriteEndArray();
    }

    // Kept as the raw text so Policy reports a bad effect itself; only a non-string is rejected here.
    private static string Text(object? element, string position) =>
        element as string
        ?? throw new PolicyLoadException($"Malformed rule tuple: {position} must be a string, got {Describe(element)}.");

    private static string Describe(object? value) => value is null ? "null" : value.GetType().Name;
}

internal sealed class PolicyTestCheckConverter : JsonConverter<PolicyTestCheck>
{
    public override PolicyTestCheck Read(ref Utf8JsonReader reader, Type typeToConvert, JsonSerializerOptions options)
    {
        if (PlainJsonConverter.ReadValue(ref reader) is not List<object?> { Count: 2 or 3 } tuple
            || tuple[0] is not string action
            || tuple[1] is not string subject)
        {
            throw new PolicyLoadException("Malformed test check: expected [action, subject] or [action, subject, claims].");
        }

        return new PolicyTestCheck(action, subject, tuple.Count == 3 ? tuple[2] : null);
    }

    public override void Write(Utf8JsonWriter writer, PolicyTestCheck value, JsonSerializerOptions options)
    {
        writer.WriteStartArray();
        writer.WriteStringValue(value.Action);
        writer.WriteStringValue(value.Subject);
        if (value.Claims is not null) PlainJsonConverter.WriteValue(writer, value.Claims);
        writer.WriteEndArray();
    }
}
