package com.cptnfizzbin.keycard.conditions;

import java.lang.System.Logger;
import java.lang.System.Logger.Level;

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

    /** Used when no {@code KeycardConfig} logger was supplied. */
    static final Logger DEFAULT_LOGGER = System.getLogger("Keycard");

    private Diagnostics() {}

    static void logTypeIssue(Logger logger, String operator, String message) {
        logger.log(Level.ERROR, DIAGNOSTIC_PREFIX + " " + operator + ": " + message);
    }

    static String typeName(Object value) {
        return value == null ? "null" : value.getClass().getSimpleName();
    }
}
