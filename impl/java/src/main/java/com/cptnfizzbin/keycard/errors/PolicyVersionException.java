package com.cptnfizzbin.keycard.errors;

import lombok.experimental.StandardException;

/**
 * Thrown by {@code Policy.from(...)} when loading a policy when a
 * PolicyDefinition's {@code version} is incompatible with what this
 * implementation supports - a different MAJOR, or a MINOR higher than
 * what's understood within a supported MAJOR.
 * {@code PATCH} never affects this decision.
 */
@StandardException
public class PolicyVersionException extends RuntimeException {
}
