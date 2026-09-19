package com.cptnfizzbin.keycard.conditions;

/**
 * "Type issues are diagnosed, not silenced." Shared by every
 * built-in operator's implementation ({@link DefaultOperators}) and {@link
 * ConditionResolver}'s field-access path - a human-readable, error-level
 * diagnostic identifying the operator and what went wrong. Used only for
 * genuine type issues, never for an ordinary non-match (a missing field,
 * an unmatched action/subject, an unregistered operator).
 */
final class Diagnostics {
    private static final String DIAGNOSTIC_PREFIX = "[KeyCard]";

    private Diagnostics() {}

    static void logTypeIssue(String operator, String message) {
        System.err.println(DIAGNOSTIC_PREFIX + " " + operator + ": " + message);
    }

    static String typeName(Object value) {
        return value == null ? "null" : value.getClass().getSimpleName();
    }
}
