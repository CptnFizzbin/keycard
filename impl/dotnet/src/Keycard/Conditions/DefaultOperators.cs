using System.Collections;
using System.Globalization;

namespace Keycard.Conditions;

internal static class DefaultOperators
{
    public static readonly IReadOnlyList<(string Name, OperatorResolver Resolver)> All = new (string, OperatorResolver)[]
    {
        ("$eq", (s, v, _) => Equality.Eq(s, v)),
        ("$ne", (s, v, _) => Equality.Ne(s, v)),
        ("$gt", (s, v, c) => NumericCompare(c, "$gt", s, v, (a, b) => a > b)),
        ("$gte", (s, v, c) => NumericCompare(c, "$gte", s, v, (a, b) => a >= b)),
        ("$lt", (s, v, c) => NumericCompare(c, "$lt", s, v, (a, b) => a < b)),
        ("$lte", (s, v, c) => NumericCompare(c, "$lte", s, v, (a, b) => a <= b)),
        ("$in", InCheck),
        ("$has", HasCheck),
        ("$substr", SubstrCheck),
        ("$or", OrCheck),
        ("$and", AndCheck),
        ("$not", (s, v, c) => !c.ResolveSubcondition(s, v)),
        ("$field", FieldOpCheck),
    };

    public static readonly IReadOnlySet<string> Names = new HashSet<string>(All.Select(o => o.Name));

    private static bool NumericCompare(IOperatorContext ctx, string op, object? subject, object? operand, Func<double, double, bool> cmp)
    {
        if (!Equality.IsNumber(subject) || !Equality.IsNumber(operand))
        {
            ctx.ReportTypeIssue(op,
                $"expected the subject and operand to both be numbers, got {Diagnostics.TypeName(subject)} and {Diagnostics.TypeName(operand)}");
            return false;
        }

        // IEEE-754 comparisons are already false for NaN, as the spec requires.
        return cmp(Equality.ToDouble(subject!), Equality.ToDouble(operand!));
    }

    private static bool InCheck(object? subject, object? operand, IOperatorContext ctx)
    {
        if (!Equality.IsSequence(operand))
        {
            ctx.ReportTypeIssue("$in", $"expected an array operand, got {Diagnostics.TypeName(operand)}");
            return false;
        }

        foreach (var item in (IEnumerable)operand!)
        {
            if (Equality.Eq(subject, item)) return true;
        }

        return false;
    }

    private static bool HasCheck(object? subject, object? value, IOperatorContext ctx)
    {
        if (!Equality.IsSequence(subject))
        {
            ctx.ReportTypeIssue("$has", $"expected an array subject, got {Diagnostics.TypeName(subject)}");
            return false;
        }

        foreach (var item in (IEnumerable)subject!)
        {
            if (Equality.Eq(item, value)) return true;
        }

        return false;
    }

    // A null subject is an ordinary non-match, not a type issue; an invalid pattern always is.
    private static bool SubstrCheck(object? subject, object? pattern, IOperatorContext ctx)
    {
        if (pattern is not string raw)
        {
            ctx.ReportTypeIssue("$substr", $"expected a string pattern, got {Diagnostics.TypeName(pattern)}");
            return false;
        }

        var parsed = SubstrPattern.Cached(raw);
        if (parsed is null)
        {
            ctx.ReportTypeIssue("$substr", $"malformed pattern: {raw}");
            return false;
        }

        return subject is not null && parsed.Matches(StringForm(subject));
    }

    private static bool OrCheck(object? subject, object? operand, IOperatorContext ctx)
    {
        if (!Equality.IsSequence(operand))
        {
            ctx.ReportTypeIssue("$or", $"expected an array operand, got {Diagnostics.TypeName(operand)}");
            return false;
        }

        var any = false;
        foreach (var condition in (IEnumerable)operand!)
        {
            any = true;
            if (ctx.ResolveSubcondition(subject, condition)) return true;
        }

        if (!any) ctx.ReportTypeIssue("$or", "empty $or is vacuously false - likely an authoring mistake");
        return false;
    }

    private static bool AndCheck(object? subject, object? operand, IOperatorContext ctx)
    {
        if (!Equality.IsSequence(operand))
        {
            ctx.ReportTypeIssue("$and", $"expected an array operand, got {Diagnostics.TypeName(operand)}");
            return false;
        }

        var any = false;
        foreach (var condition in (IEnumerable)operand!)
        {
            any = true;
            if (!ctx.ResolveSubcondition(subject, condition)) return false;
        }

        if (!any) ctx.ReportTypeIssue("$and", "empty $and is vacuously true - likely an authoring mistake");
        return true;
    }

    // Explicit field access, for a field whose own name starts with "$".
    private static bool FieldOpCheck(object? subject, object? operand, IOperatorContext ctx)
    {
        if (operand is IList { Count: 2 } tuple && tuple[0] is string name)
        {
            return FieldAccess.Check(subject, name, tuple[1], ctx);
        }

        ctx.ReportTypeIssue("$field", $"expected a [name, Condition] tuple, got {operand}");
        return false;
    }

    // Booleans are lowercased so the string form matches the other implementations'.
    private static string StringForm(object value) => value switch
    {
        string s => s,
        bool b => b ? "true" : "false",
        IFormattable f => f.ToString(null, CultureInfo.InvariantCulture),
        _ => value.ToString() ?? string.Empty,
    };
}
