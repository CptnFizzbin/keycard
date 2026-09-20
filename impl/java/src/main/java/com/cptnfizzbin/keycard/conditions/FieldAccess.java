package com.cptnfizzbin.keycard.conditions;

import java.util.Map;
import java.util.Set;

/**
 * bare-key and `$field` long-form field access - shared
 * by {@link ConditionResolver}'s non-`$`-prefixed dispatch and the `$field`
 * {@link Operator} in {@link DefaultOperators}, since both resolve to the
 * exact same "look up a named field on the subject, then recurse" behavior.
 */
final class FieldAccess {
    private FieldAccess() {
    }

    /**
     * a missing field (or a non-object subject) makes the
     * whole field-condition false - absence, not a type issue - with one
     * exception: {@code $ne}, which MUST be the exact negation of
     * {@code $eq} even when the field is missing, since {@code $eq} on a
     * missing field is false. See {@link #isBareNe}.
     *
     * <p>v1 supports only top-level field access: a field condition MUST NOT
     * itself narrow into another field, so {@code ctx.canNarrowField()} MUST
     * still be {@code true} at this point in the tree. When it isn't - a
     * field condition already reached by one narrowing step attempting a
     * second - this is a structural problem, not a data one, so it's
     * diagnosed like any other malformed condition shape rather than
     * silently returning {@code false}.
     *
     * <p>When {@code ctx} is a {@link ConditionResolver.Ctx} carrying a
     * {@link com.cptnfizzbin.keycard.subject.SubjectFieldMapper} - only ever
     * true here, since a mapper-carrying context is only ever built for
     * {@code canNarrowField() == true} (permits only one level of
     * narrowing, and only field access, never {@code $and}/{@code $or}/
     * {@code $not}, narrows {@code subject} at all) - it's tried first for
     * {@code fieldName}; a field it doesn't define falls through to the
     * Map/reflection path below.
     */
    static boolean check(Object subject, String fieldName, Object condition, OperatorContext ctx) {
        if (!ctx.canNarrowField()) {
            Diagnostics.logTypeIssue(fieldName,
                "v1 supports only top-level field access - a field condition can't itself narrow into another field");
            return false;
        }

        if (subject instanceof Map) {
            Map<?, ?> map = (Map<?, ?>) subject;
            if (!map.containsKey(fieldName)) return isBareNe(condition);
            return ctx.resolveFieldSubcondition(map.get(fieldName), condition);
        }
        if (subject == null) {
            return isBareNe(condition);
        }
        try {
            java.lang.reflect.Field field = subject.getClass().getDeclaredField(fieldName);
            field.setAccessible(true);
            Object subjectValue = field.get(subject);
            return ctx.resolveFieldSubcondition(subjectValue, condition);
        } catch (NoSuchFieldException | IllegalAccessException e) {
            return isBareNe(condition);
        }
    }

    /**
     * SPEC_V0.md `$ne`-on-a-missing-field carve-out is narrow: it
     * only fires when `$ne` is itself the sole nested condition being
     * evaluated at the missing field, not when it's one key among several
     * in a multi-key condition object or nested deeper -
     * {@code { status: { $not: { $eq: "archived" } } } } on a missing
     * {@code status} still gets the blanket {@code false}, unlike a bare
     * {@code { status: { $ne: "archived" } } }, even though {@code $not}
     * has the same "exact negation" contract {@code $ne} does.
     * Undecided whether that should change; not addressed here.
     */
    private static boolean isBareNe(Object condition) {
        return condition instanceof Map && ((Map<?, ?>) condition).keySet().equals(Set.of("$ne"));
    }
}
