package com.cptnfizzbin.keycard.conditions;

/**
 * A flat {@code (subjectValue, value) -> boolean} custom operator check -
 * the common case, for a custom operator that just compares two values and
 * has no need to recurse into the condition language. Registered via
 * {@link OperatorCatalog#set(String, ConditionOperator)}, which adapts it
 * into a full {@link Operator} internally; a custom operator that does need
 * to recurse (its own {@code $and}/{@code $or}-like logic) uses {@link
 * Operator}/{@link OperatorContext} directly instead.
 */
@FunctionalInterface
public interface ConditionOperator {
    boolean resolve(Object subjectValue, Object value);
}
