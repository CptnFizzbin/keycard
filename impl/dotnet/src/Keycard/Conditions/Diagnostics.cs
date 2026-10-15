using Microsoft.Extensions.Logging;

namespace Keycard.Conditions;

/// <summary>
/// Error-level diagnostics for genuine type issues in a condition. Never used
/// for an ordinary non-match (a missing field, an unmatched action or subject,
/// an unregistered operator).
/// </summary>
internal static class Diagnostics
{
    public static void LogTypeIssue(ILogger logger, string operatorName, string message) =>
        logger.LogError("[KeyCard] {Operator}: {Message}", operatorName, message);

    public static string TypeName(object? value) => value is null ? "null" : value.GetType().Name;
}
