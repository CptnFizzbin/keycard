package com.cptnfizzbin.keycard.conditions;

import java.util.Collection;
import java.util.List;
import java.util.Set;
import java.util.function.BiPredicate;
import java.util.stream.Collectors;

/**
 * Every operator {@link ConditionResolver} understands natively,
 * as {@link Operator} instances - the
 * same type a custom, host-application-supplied operator uses, so built-in
 * and custom operators are constructed, registered, and dispatched
 * identically.
 */
final class DefaultOperators {
    private DefaultOperators() {}

    static final List<Operator> ALL = List.of(
        Operator.of("$eq", (s, v, ctx) -> StringConditions.eq(s, v)),
        Operator.of("$ne", (s, v, ctx) -> StringConditions.ne(s, v)),
        Operator.of("$gt", (s, v, ctx) -> numericCompare(ctx, "$gt", s, v, (a, b) -> a > b)),
        Operator.of("$gte", (s, v, ctx) -> numericCompare(ctx, "$gte", s, v, (a, b) -> a >= b)),
        Operator.of("$lt", (s, v, ctx) -> numericCompare(ctx, "$lt", s, v, (a, b) -> a < b)),
        Operator.of("$lte", (s, v, ctx) -> numericCompare(ctx, "$lte", s, v, (a, b) -> a <= b)),
        Operator.of("$in", (s, v, ctx) -> inCheck(ctx, s, v)),
        Operator.of("$has", (s, v, ctx) -> hasCheck(ctx, s, v)),
        Operator.of("$substr", (s, v, ctx) -> substrCheck(ctx, s, v)),
        Operator.of("$or", (s, v, ctx) -> orCheck(ctx, s, v)),
        Operator.of("$and", (s, v, ctx) -> andCheck(ctx, s, v)),
        Operator.of("$not", (s, v, ctx) -> LogicConditions.not(ctx, s, v)),
        Operator.of("$field", (s, v, ctx) -> fieldOpCheck(ctx, s, v))
    );

    /** Every built-in operator name - the single source of truth for "is this name built-in". */
    static final Set<String> NAMES = ALL.stream().map(Operator::name).collect(Collectors.toUnmodifiableSet());

    /** $gt/$gte/$lt/$lte - numeric-only, IEEE-754 double semantics. */
    private static boolean numericCompare(OperatorContext ctx, String op, Object subject, Object operand, BiPredicate<Double, Double> cmp) {
        if (!(subject instanceof Number) || !(operand instanceof Number)) {
            ctx.reportTypeIssue(op, "expected the subject and operand to both be numbers, got "
                + Diagnostics.typeName(subject) + " and " + Diagnostics.typeName(operand));
            return false;
        }
        double a = ((Number) subject).doubleValue();
        double b = ((Number) operand).doubleValue();
        return cmp.test(a, b);
    }

    /** $in - operand must be a collection (any {@link Collection}, e.g. a {@code Set}); containment uses $eq semantics per element. */
    private static boolean inCheck(OperatorContext ctx, Object subject, Object operand) {
        if (!(operand instanceof Collection<?> collection)) {
            ctx.reportTypeIssue("$in", "expected an array operand, got " + Diagnostics.typeName(operand));
            return false;
        }
        return GroupConditions.in(subject, collection);
    }

    /** $has - subject must be a collection (any {@link Collection}, e.g. a {@code Set}). */
    private static boolean hasCheck(OperatorContext ctx, Object subject, Object value) {
        if (!(subject instanceof Collection<?> collection)) {
            ctx.reportTypeIssue("$has", "expected an array subject, got " + Diagnostics.typeName(subject));
            return false;
        }
        return GroupConditions.has(collection, value);
    }

    /** $substr - a null subject is an ordinary non-match, not a type issue; an invalid pattern always is. */
    private static boolean substrCheck(OperatorContext ctx, Object subject, Object pattern) {
        if (!(pattern instanceof String)) {
            ctx.reportTypeIssue("$substr", "expected a string pattern, got " + Diagnostics.typeName(pattern));
            return false;
        }
        SubstrPattern parsed = SubstrPattern.cached((String) pattern);
        if (parsed == null) {
            ctx.reportTypeIssue("$substr", "malformed pattern: " + pattern);
            return false;
        }
        if (subject == null) {
            return false;
        }
        return parsed.matches(String.valueOf(subject));
    }

    /** $or - operand must be an array; {@code $or: []} is vacuously false. */
    private static boolean orCheck(OperatorContext ctx, Object subject, Object operand) {
        if (!(operand instanceof List)) {
            ctx.reportTypeIssue("$or", "expected an array operand, got " + Diagnostics.typeName(operand));
            return false;
        }
        List<?> list = (List<?>) operand;
        if (list.isEmpty()) {
            ctx.reportTypeIssue("$or", "empty $or is vacuously false - likely an authoring mistake");
            return false;
        }
        return LogicConditions.or(ctx, subject, list);
    }

    /** $and - operand must be an array; {@code $and: []} is vacuously true. */
    private static boolean andCheck(OperatorContext ctx, Object subject, Object operand) {
        if (!(operand instanceof List)) {
            ctx.reportTypeIssue("$and", "expected an array operand, got " + Diagnostics.typeName(operand));
            return false;
        }
        List<?> list = (List<?>) operand;
        if (list.isEmpty()) {
            ctx.reportTypeIssue("$and", "empty $and is vacuously true - likely an authoring mistake");
            return true;
        }
        return LogicConditions.and(ctx, subject, list);
    }

    /** $field - explicit field access, for a field whose name itself starts with "$". */
    private static boolean fieldOpCheck(OperatorContext ctx, Object subject, Object operand) {
        if (!(operand instanceof List) || ((List<?>) operand).size() != 2 || !(((List<?>) operand).get(0) instanceof String)) {
            ctx.reportTypeIssue("$field", "expected a [name, Condition] tuple, got " + operand);
            return false;
        }
        List<?> tuple = (List<?>) operand;
        return FieldAccess.check(subject, (String) tuple.get(0), tuple.get(1), ctx);
    }
}
