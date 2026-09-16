package com.cptnfizzbin.keycard.conditions;

import java.util.Map;
import java.util.Set;

/**
 * §7.4.10, §7.4.11: bare-key and `$field` long-form field access - shared
 * by {@link ConditionResolver}'s non-`$`-prefixed dispatch and the `$field`
 * {@link Operator} in {@link DefaultOperators}, since both resolve to the
 * exact same "look up a named field on the subject, then recurse" behavior.
 */
final class FieldAccess {
    private FieldAccess() {}

    /**
     * §7.4.10, §7.3: a missing field (or a non-object subject) makes the
     * whole field-condition false - absence, not a type issue - with one
     * exception: {@code $ne} (§7.4.2), which MUST be the exact negation of
     * {@code $eq} even when the field is missing, since {@code $eq} on a
     * missing field is false. See {@link #isBareNe}.
     *
     * <p>When {@code ctx} is a {@link ConditionResolver.MappedContext} bound
     * to exactly {@code subject} (reference-identical to its {@code
     * rootSubject} - never a value a previous field lookup narrowed into),
     * its {@link SubjectFieldMapper} is tried first; a field it doesn't
     * define falls through to the Map/reflection path below.
     */
    static boolean check(Object subject, String fieldName, Object condition, OperatorContext ctx) {
        if (ctx instanceof ConditionResolver.MappedContext mapped
                && mapped.rootSubject == subject
                && mapped.fieldMapper.hasField(fieldName)) {
            return ctx.resolveSubcondition(mapped.fieldMapper.get(subject, fieldName), condition);
        }

        if (subject instanceof Map) {
            Map<?, ?> map = (Map<?, ?>) subject;
            if (!map.containsKey(fieldName)) return isBareNe(condition);
            return ctx.resolveSubcondition(map.get(fieldName), condition);
        }
        if (subject == null) {
            return isBareNe(condition);
        }
        try {
            java.lang.reflect.Field field = subject.getClass().getDeclaredField(fieldName);
            field.setAccessible(true);
            Object subjectValue = field.get(subject);
            return ctx.resolveSubcondition(subjectValue, condition);
        } catch (NoSuchFieldException | IllegalAccessException e) {
            return isBareNe(condition);
        }
    }

    /**
     * SPEC_V1-0-0.md §7.3's `$ne`-on-a-missing-field carve-out is narrow: it
     * only fires when `$ne` is itself the sole nested condition being
     * evaluated at the missing field, not when it's one key among several
     * in a multi-key condition object (§7.5) or nested deeper -
     * {@code { status: { $not: { $eq: "archived" } } } } on a missing
     * {@code status} still gets the blanket {@code false}, unlike a bare
     * {@code { status: { $ne: "archived" } } }, even though {@code $not}
     * has the same "exact negation" contract {@code $ne} does (§7.4.9).
     * Undecided whether that should change; not addressed here.
     */
    private static boolean isBareNe(Object condition) {
        return condition instanceof Map && ((Map<?, ?>) condition).keySet().equals(Set.of("$ne"));
    }
}
