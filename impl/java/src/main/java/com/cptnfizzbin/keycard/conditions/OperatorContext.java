package com.cptnfizzbin.keycard.conditions;

/**
 * Passed to every {@link Operator}'s {@code resolve} call - built-in and
 * custom alike - so it can recurse into the condition language exactly the
 * way $and/$or/$not do. This is what gives a
 * custom, host-application-supplied operator the same recursive power a
 * built-in one has, rather than being limited to a flat
 * {@code (subject, value) -> boolean} check.
 */
public interface OperatorContext {
    /**
     * Evaluates {@code condition} against {@code subject}, preserving whether
     * this point in the tree may still narrow into a field - used
     * by $and/$or/$not, which don't themselves narrow.
     */
    boolean resolveSubcondition(Object subject, Object condition);

    /**
     * Evaluates {@code condition} against a subject already narrowed by one
     * field access, disabling any further field narrowing beneath it
     * - used by the bare-key field path and {@code $field}.
     */
    boolean resolveFieldSubcondition(Object subject, Object condition);

    /**
     * True if a field condition (bare-key or {@code $field}) is still
     * allowed to narrow at this point in the tree - v1 permits exactly one
     * level.
     */
    boolean canNarrowField();
}
