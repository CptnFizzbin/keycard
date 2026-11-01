using System.Linq.Expressions;
using System.Reflection;
using Keycard.Errors;

namespace Keycard.Conditions;

/// <summary>
/// A condition over the claims type <typeparamref name="TClaims"/>. Fields are
/// chosen with a lambda (<c>c =&gt; c.OwnerId</c>) and values are checked against
/// the field's type, so a misspelled field or a mistyped value is a compile
/// error. A field is written into the policy under its <see cref="ClaimNameAttribute"/>
/// name, or else its camelCased name (<c>OwnerId</c> becomes <c>ownerId</c>), the
/// spelling other languages' implementations use.
/// </summary>
/// <example>
/// <code>
/// builder.Allow(Action.Update, ArticleSubject.Def, Condition&lt;ArticleClaims&gt;.Or(
///     Condition&lt;ArticleClaims&gt;.Eq(c =&gt; c.OwnerId, user.Id),
///     Condition&lt;ArticleClaims&gt;.In(c =&gt; c.Status, "draft", "published")));
/// </code>
/// </example>
/// <typeparam name="TClaims">The claims type of the subject the condition applies to.</typeparam>
public sealed class Condition<TClaims> : ICondition
{
    private readonly IReadOnlyDictionary<string, object?> _map;

    private Condition(IReadOnlyDictionary<string, object?> map) => _map = map;

    public IReadOnlyDictionary<string, object?> ToMap() => _map;

    public static Condition<TClaims> Eq<TValue>(Expression<Func<TClaims, TValue>> field, TValue value) =>
        new(ConditionMaps.Field(FieldName(field), "$eq", value));

    public static Condition<TClaims> Ne<TValue>(Expression<Func<TClaims, TValue>> field, TValue value) =>
        new(ConditionMaps.Field(FieldName(field), "$ne", value));

    public static Condition<TClaims> Gt<TValue>(Expression<Func<TClaims, TValue>> field, TValue value)
        where TValue : System.Numerics.INumber<TValue> =>
        new(ConditionMaps.Field(FieldName(field), "$gt", value));

    public static Condition<TClaims> Gte<TValue>(Expression<Func<TClaims, TValue>> field, TValue value)
        where TValue : System.Numerics.INumber<TValue> =>
        new(ConditionMaps.Field(FieldName(field), "$gte", value));

    public static Condition<TClaims> Lt<TValue>(Expression<Func<TClaims, TValue>> field, TValue value)
        where TValue : System.Numerics.INumber<TValue> =>
        new(ConditionMaps.Field(FieldName(field), "$lt", value));

    public static Condition<TClaims> Lte<TValue>(Expression<Func<TClaims, TValue>> field, TValue value)
        where TValue : System.Numerics.INumber<TValue> =>
        new(ConditionMaps.Field(FieldName(field), "$lte", value));

    /// <summary>Matches when the field's value is one of <paramref name="values"/>.</summary>
    public static Condition<TClaims> In<TValue>(Expression<Func<TClaims, TValue>> field, params TValue[] values) =>
        new(ConditionMaps.Field(FieldName(field), "$in", values.Cast<object?>().ToList()));

    /// <summary>Matches when the field is a collection containing <paramref name="value"/>.</summary>
    public static Condition<TClaims> Has<TItem>(Expression<Func<TClaims, IEnumerable<TItem>>> field, TItem value) =>
        new(ConditionMaps.Field(FieldName(field), "$has", value));

    /// <summary>
    /// Matches when the field's string form matches <paramref name="pattern"/>:
    /// <c>*</c> matches any run of characters, a leading <c>^</c> or trailing
    /// <c>$</c> anchors, and a backslash escapes the next character. An
    /// unanchored pattern matches anywhere in the value.
    /// </summary>
    public static Condition<TClaims> Substr(Expression<Func<TClaims, string?>> field, string pattern) =>
        new(ConditionMaps.Field(FieldName(field), "$substr", pattern));

    public static Condition<TClaims> And(params Condition<TClaims>[] conditions) =>
        new(ConditionMaps.Group("$and", conditions));

    public static Condition<TClaims> Or(params Condition<TClaims>[] conditions) =>
        new(ConditionMaps.Group("$or", conditions));

    public static Condition<TClaims> Not(Condition<TClaims> condition) => new(ConditionMaps.Not(condition));

    /// <summary>A custom (or built-in) operator applied to the whole claims object.</summary>
    public static Condition<TClaims> Op(string operatorName, object? value) =>
        new(ConditionMaps.Whole(operatorName, value));

    /// <summary>A custom (or built-in) operator scoped to a field, e.g. <c>Condition&lt;Claims&gt;.Op(c =&gt; c.CreatedAt, "$withinDays", 30)</c>.</summary>
    public static Condition<TClaims> Op<TValue>(Expression<Func<TClaims, TValue>> field, string operatorName, object? value) =>
        new(ConditionMaps.Field(FieldName(field), operatorName, value));

    private static string FieldName(LambdaExpression selector)
    {
        // Convert nodes appear when the compiler widens the field to the
        // delegate's declared type, e.g. string[] to IEnumerable<string>.
        var body = selector.Body;
        while (body is UnaryExpression { NodeType: ExpressionType.Convert or ExpressionType.ConvertChecked } unary)
        {
            body = unary.Operand;
        }

        if (body is not MemberExpression { Member: PropertyInfo or FieldInfo } member
            || member.Expression != selector.Parameters[0])
        {
            throw new PolicyArgumentException(
                "A condition field must be a direct property or field of the claims, e.g. c => c.OwnerId; got " + selector + ".");
        }

        var claimName = member.Member.GetCustomAttribute<ClaimNameAttribute>()?.Name;
        return claimName ?? char.ToLowerInvariant(member.Member.Name[0]) + member.Member.Name[1..];
    }
}
