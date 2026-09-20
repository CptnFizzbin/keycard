package com.cptnfizzbin.keycard.conditions;

import java.util.Collection;

/**
 * Pure containment logic for $in/$has -
 * type-checking the operand/subject and the required diagnostic on
 * failure is the caller's job (ConditionResolver), so these assume an
 * already-validated {@link Collection}.
 */
public final class GroupConditions {
    private GroupConditions() {}

    /** Containment MUST use the same equality semantics as $eq per element. */
    public static boolean in(Object subject, Collection<?> array) {
        for (Object v : array) {
            if (StringConditions.eq(subject, v)) return true;
        }
        return false;
    }

    public static boolean has(Collection<?> subject, Object value) {
        for (Object v : subject) {
            if (StringConditions.eq(v, value)) return true;
        }
        return false;
    }
}
