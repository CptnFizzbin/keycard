package com.cptnfizzbin.keycard.conditions;

import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

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
     * Resolved accessors, cached per subject class and field name - an empty
     * {@code Optional} records "no such field" so a miss isn't re-reflected
     * on every evaluation either.
     */
    private static final ClassValue<Map<String, Optional<Accessor>>> ACCESSORS = new ClassValue<>() {
        @Override
        protected Map<String, Optional<Accessor>> computeValue(Class<?> type) {
            return new ConcurrentHashMap<>();
        }
    };

    @FunctionalInterface
    private interface Accessor {
        Object read(Object target) throws ReflectiveOperationException;
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
     * <p>A {@link Map} subject is read by key. Any other object is read by
     * {@link #findAccessor}: a field declared on its class or any superclass
     * first, then a public no-arg accessor method ({@code name()},
     * {@code getName()}, {@code isName()}).
     */
    static boolean check(Object subject, String fieldName, Object condition, OperatorContext ctx) {
        if (!ctx.canNarrowField()) {
            ctx.reportTypeIssue(fieldName,
                "v1 supports only top-level field access - a field condition can't itself narrow into another field");
            return false;
        }

        if (subject instanceof Map<?, ?> map) {
            if (!map.containsKey(fieldName)) return isBareNe(condition);
            return ctx.resolveFieldSubcondition(map.get(fieldName), condition);
        }
        if (subject == null) {
            return isBareNe(condition);
        }

        Optional<Accessor> accessor = ACCESSORS.get(subject.getClass())
            .computeIfAbsent(fieldName, name -> findAccessor(subject.getClass(), name));
        if (accessor.isEmpty()) {
            return isBareNe(condition);
        }

        Object subjectValue;
        try {
            subjectValue = accessor.get().read(subject);
        } catch (InvocationTargetException e) {
            ctx.reportTypeIssue(fieldName, "reading the field threw " + e.getCause());
            return false;
        } catch (ReflectiveOperationException e) {
            return isBareNe(condition);
        }
        return ctx.resolveFieldSubcondition(subjectValue, condition);
    }

    /**
     * {@code trySetAccessible} (rather than {@code setAccessible}) means a
     * member the module system won't open is skipped instead of throwing
     * {@code InaccessibleObjectException} out of {@code Policy.can}.
     */
    private static Optional<Accessor> findAccessor(Class<?> type, String name) {
        if (name.isEmpty()) return Optional.empty();

        for (Class<?> c = type; c != null && c != Object.class; c = c.getSuperclass()) {
            try {
                Field field = c.getDeclaredField(name);
                if (!Modifier.isStatic(field.getModifiers()) && field.trySetAccessible()) {
                    return Optional.of(field::get);
                }
            } catch (NoSuchFieldException ignored) {
                // keep walking up the hierarchy
            }
        }

        String capitalized = Character.toUpperCase(name.charAt(0)) + name.substring(1);
        for (String candidate : List.of(name, "get" + capitalized, "is" + capitalized)) {
            try {
                Method method = type.getMethod(candidate);
                // Object's own methods (getClass(), hashCode(), ...) are never subject fields.
                if (method.getDeclaringClass() != Object.class && !Modifier.isStatic(method.getModifiers())
                    && method.getReturnType() != void.class && method.trySetAccessible()) {
                    return Optional.of(method::invoke);
                }
            } catch (NoSuchMethodException ignored) {
                // try the next naming convention
            }
        }
        return Optional.empty();
    }

    /**
     * The `$ne`-on-a-missing-field carve-out is narrow: it
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
