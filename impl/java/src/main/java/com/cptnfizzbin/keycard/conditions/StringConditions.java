package com.cptnfizzbin.keycard.conditions;

import java.math.BigDecimal;

public final class StringConditions {
    private StringConditions() {}

    /**
     * value equality for primitives - not reference/identity
     * equality. Implementations SHOULD ensure NaN never equals itself
     * under $eq/$ne, even where the host language's default equality
     * would say otherwise - {@code Double.equals} treats NaN as equal to
     * NaN, so that's special-cased here rather than left to leak in.
     */
    public static boolean eq(Object subject, Object expected) {
        if (subject instanceof Number a && expected instanceof Number b) {
            return numericEq(a, b);
        }
        if (subject == null || expected == null) {
            return subject == expected;
        }
        return subject.equals(expected);
    }

    /**
     * Numbers compare by value, not by boxed type - {@code Integer.equals(Long)}
     * is always false, but a claims field declared {@code long} MUST still
     * equal the {@code Integer} a JSON/YAML parser produces for the same
     * literal. Integral values compare exactly (no precision loss above
     * 2^53); anything involving a float/double uses IEEE-754 {@code ==},
     * under which NaN never equals anything, itself included.
     */
    private static boolean numericEq(Number a, Number b) {
        if (isFloatingPoint(a) || isFloatingPoint(b)) {
            return a.doubleValue() == b.doubleValue();
        }
        return toBigDecimal(a).compareTo(toBigDecimal(b)) == 0;
    }

    private static boolean isFloatingPoint(Number n) {
        return n instanceof Double || n instanceof Float;
    }

    private static BigDecimal toBigDecimal(Number n) {
        return n instanceof BigDecimal bd ? bd : new BigDecimal(n.toString());
    }

    public static boolean ne(Object subject, Object expected) {
        return !eq(subject, expected);
    }
}
