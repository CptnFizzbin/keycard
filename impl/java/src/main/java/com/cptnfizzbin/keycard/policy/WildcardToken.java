package com.cptnfizzbin.keycard.policy;

import com.cptnfizzbin.keycard.errors.PolicyLoadException;

/**
 * Type-state for a declared {@code meta.anyAction}/{@code meta.anySubject}
 * position (SPEC_V0.md §3.2.1, §4, §5) - replaces the previous
 * {@code anyActionDeclared}/{@code anyAction} boolean-pair workaround on
 * {@link PolicyDefinition.Meta} with a proper sum type. "Not declared at
 * all" isn't one of these states; it's represented by a {@code null}
 * {@link PolicyDefinition.Meta#getAnyAction()}/{@code getAnySubject()}, so
 * {@link Wildcards} can tell "absent, defaults to _ANY_" apart from either
 * state below.
 */
public sealed interface WildcardToken {
    /** The wildcard mechanism is disabled for this position - no string, including {@code "_ANY_"}, has special meaning. */
    record Disabled() implements WildcardToken {}

    /** An explicit wildcard token string. */
    record Named(String token) implements WildcardToken {}

    Disabled DISABLED = new Disabled();

    /**
     * Four-way dispatch for a raw, untyped declaration (SPEC_V0.md
     * §3.2.1): {@code null} or {@code false} disables the wildcard
     * position; a {@link String} names an explicit token; anything else -
     * a number, {@code true}, a list, ... - is invalid and MUST throw
     * immediately rather than being silently coerced or passed through as
     * a raw value. ("Undeclared" isn't a case here at all - it's the
     * absence of a call to this method, i.e. the field simply staying
     * {@code null} on {@code Meta}.)
     */
    static WildcardToken of(Object raw) {
        if (raw == null) return DISABLED;
        if (raw instanceof Boolean) {
            if (Boolean.FALSE.equals(raw)) return DISABLED;
            throw new PolicyLoadException(
                "meta.anyAction/meta.anySubject: \"true\" is not a valid declaration - use a string token to name"
                    + " a wildcard, or null/false to disable it (SPEC_V0.md §3.2.1)."
            );
        }
        if (raw instanceof String) return new Named((String) raw);
        throw new PolicyLoadException(
            "meta.anyAction/meta.anySubject: expected a string, null, or false, got "
                + raw.getClass().getSimpleName() + " (SPEC_V0.md §3.2.1)."
        );
    }
}
