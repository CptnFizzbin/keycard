package com.cptnfizzbin.keycard.errors;

import lombok.experimental.StandardException;

/**
 * Thrown by {@code new Policy(...)} when loading a policy when a
 * PolicyDefinition is structurally invalid - a
 * malformed rule tuple, a both-sides-wildcarded rule
 * carrying a Conditions element, or a rule
 * referencing an action/subject/custom-operator name outside a declared
 * {@code meta} catalog.
 */
@StandardException
public class PolicyLoadException extends RuntimeException {
}
