package com.cptnfizzbin.keycard.errors;

import lombok.experimental.StandardException;

/**
 * Thrown immediately by {@code PolicyBuilder}'s {@code allow()}/{@code
 * deny()} when called with a rule wildcarded on both the action and the
 * subject that also carries a Conditions element. Callers get this at the call
 * site, rather than waiting for {@code buildDef()}/{@code new Policy(...)}
 * to eventually catch it.
 */
@StandardException
public class PolicyArgumentException extends IllegalArgumentException {
}
