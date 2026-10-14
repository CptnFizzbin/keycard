using System.Text.RegularExpressions;
using Keycard.Errors;

namespace Keycard;

/// <summary>The policy spec version this implementation targets.</summary>
public static partial class KeycardVersion
{
    /// <summary>The policy spec version stamped onto every <see cref="PolicyDefinition"/> a <see cref="PolicyBuilder"/> creates.</summary>
    public const string PolicyVersion = "0.1";

    private const int SupportedMajor = 0;
    private const int SupportedMinor = 1;

    [GeneratedRegex(@"^(\d+)\.(\d+)(?:\.(\d+))?(?:[-+].*)?$")]
    private static partial Regex SemVer();

    /// <summary>
    /// Throws a <see cref="PolicyVersionException"/> unless <paramref name="version"/>
    /// is a SemVer string with the supported MAJOR and a MINOR no higher than
    /// this implementation's. PATCH is ignored and may be omitted.
    /// </summary>
    public static void AssertSupported(string? version)
    {
        var match = version is null ? null : SemVer().Match(version);
        if (match is not { Success: true })
        {
            throw new PolicyVersionException($"Invalid version {(version is null ? "null" : $"\"{version}\"")}");
        }

        var major = int.Parse(match.Groups[1].Value);
        var minor = int.Parse(match.Groups[2].Value);
        if (major != SupportedMajor || minor > SupportedMinor)
        {
            throw new PolicyVersionException(
                $"Unsupported policy version \"{version}\": this implementation supports {SupportedMajor}.0.0 through {SupportedMajor}.{SupportedMinor}.x");
        }
    }
}
