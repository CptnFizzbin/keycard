package com.cptnfizzbin.keycard.errors;

import lombok.experimental.StandardException;

/**
 * Thrown by {@code Policy.from(...)} when loading a policy when a
 * PolicyDefinition is structurally invalid per SPEC_V0.md - a
 * malformed rule tuple, a both-sides-wildcarded rule
 * carrying a Conditions element (property 5, EC-6), or a rule
 * referencing an action/subject/custom-operator name outside a declared
 * {@code meta} catalog.
 */
@StandardException
public class PolicyLoadException extends RuntimeException {
}
