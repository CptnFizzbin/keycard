using System.Collections;

namespace Keycard.Conditions;

/// <summary>The <c>$eq</c> value-equality rules shared by every operator that compares values.</summary>
public static class Equality
{
    /// <summary>
    /// Returns whether <paramref name="subject"/> equals <paramref name="expected"/>.
    /// Numbers compare by value regardless of CLR type, <c>NaN</c> never equals
    /// anything (itself included), and <c>null</c> equals only <c>null</c>.
    /// </summary>
    public static bool Eq(object? subject, object? expected)
    {
        if (IsNumber(subject) && IsNumber(expected))
        {
            return NumericEq(subject!, expected!);
        }

        if (subject is null || expected is null)
        {
            return subject is null && expected is null;
        }

        return subject.Equals(expected);
    }

    /// <summary>The exact negation of <see cref="Eq"/>.</summary>
    public static bool Ne(object? subject, object? expected) => !Eq(subject, expected);

    /// <summary>Returns whether <paramref name="value"/> is one of the CLR numeric types.</summary>
    public static bool IsNumber(object? value) => value is
        sbyte or byte or short or ushort or int or uint or long or ulong or float or double or decimal;

    /// <summary>Returns the IEEE-754 double value of a number, as used by <c>$gt</c>/<c>$gte</c>/<c>$lt</c>/<c>$lte</c>.</summary>
    public static double ToDouble(object number) => Convert.ToDouble(number, System.Globalization.CultureInfo.InvariantCulture);

    /// <summary>Returns whether <paramref name="value"/> is a sequence that <c>$in</c>/<c>$has</c> can iterate: any <see cref="IEnumerable"/> that is neither a string nor a map.</summary>
    public static bool IsSequence(object? value) => value is IEnumerable and not string and not IDictionary;

    private static bool NumericEq(object a, object b)
    {
        // Not a plain Equals: boxed Int32.Equals(Int64) is false, but a claims
        // field typed long must equal the int a JSON parser produces for the
        // same literal. IEEE-754 == (unlike Double.Equals) also keeps NaN
        // unequal to itself, as the spec requires.
        if (a is float or double || b is float or double)
        {
            return ToDouble(a) == ToDouble(b);
        }

        return Convert.ToDecimal(a, System.Globalization.CultureInfo.InvariantCulture)
            == Convert.ToDecimal(b, System.Globalization.CultureInfo.InvariantCulture);
    }
}
