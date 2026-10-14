using System.Collections.Concurrent;
using System.Text;
using System.Text.RegularExpressions;

namespace Keycard.Conditions;

/// <summary>A parsed <c>$substr</c> pattern; a trailing backslash is ignored.</summary>
internal sealed class SubstrPattern
{
    private const int CacheLimit = 256;

    // Bounded because patterns built from per-request data could otherwise
    // grow it without limit; a null entry caches a malformed pattern.
    private static readonly ConcurrentDictionary<string, SubstrPattern?> Cache = new();

    private readonly Regex _compiled;

    private SubstrPattern(Regex compiled) => _compiled = compiled;

    /// <summary>Same as <see cref="Parse"/>, but memoized.</summary>
    public static SubstrPattern? Cached(string raw)
    {
        if (Cache.TryGetValue(raw, out var hit)) return hit;

        var parsed = Parse(raw);
        if (Cache.Count >= CacheLimit) Cache.Clear();
        Cache[raw] = parsed;
        return parsed;
    }

    /// <summary>
    /// Returns the parsed pattern, or <c>null</c> if it is malformed: an
    /// unescaped <c>^</c> anywhere but first, or an unescaped <c>$</c>
    /// anywhere but last.
    /// </summary>
    public static SubstrPattern? Parse(string raw)
    {
        var regex = new StringBuilder();
        var n = raw.Length;

        for (var i = 0; i < n; i++)
        {
            var c = raw[i];
            switch (c)
            {
                case '\\':
                    if (i + 1 >= n) break;
                    regex.Append(Regex.Escape(raw[i + 1].ToString()));
                    i++;
                    break;
                case '*':
                    regex.Append(".*");
                    break;
                case '^':
                    if (i != 0) return null;
                    regex.Append('^');
                    break;
                case '$':
                    if (i != n - 1) return null;
                    // Not "$": in .NET it also matches before a trailing newline.
                    regex.Append("\\z");
                    break;
                default:
                    regex.Append(Regex.Escape(c.ToString()));
                    break;
            }
        }

        // Singleline so "." (from ".*") truly means "any character".
        return new SubstrPattern(new Regex(regex.ToString(), RegexOptions.Singleline | RegexOptions.CultureInvariant));
    }

    public bool Matches(string subject) => _compiled.IsMatch(subject);
}
