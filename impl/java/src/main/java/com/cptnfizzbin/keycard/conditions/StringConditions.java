package com.cptnfizzbin.keycard.conditions;

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
        if (subject instanceof Number && expected instanceof Number) {
            double a = ((Number) subject).doubleValue();
            double b = ((Number) expected).doubleValue();
            if (Double.isNaN(a) || Double.isNaN(b)) return false;
        }
        if (subject == null || expected == null) {
            return subject == expected;
        }
        return subject.equals(expected);
    }

    public static boolean ne(Object subject, Object expected) {
        return !eq(subject, expected);
    }
}
