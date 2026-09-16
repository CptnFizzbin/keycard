package com.cptnfizzbin.keycard.policy;

/**
 * Shared wildcard-token resolution for {@link Policy} and {@code
 * PolicyBuilder} - SPEC_V1-0.md §3.2.1, §4, §5, §6.
 */
public final class Wildcards {
    private Wildcards() {}

    private static final WildcardToken.Named DEFAULT_WILDCARD = new WildcardToken.Named("_ANY_");

    /**
     * A declared {@link WildcardToken}, or the "_ANY_" default when none
     * was declared ({@code null}) - the shared building block behind
     * {@link #effectiveAnyAction}/{@link #effectiveAnySubject}, and
     * reusable wherever a token needs resolving before a {@code Meta}
     * exists yet (e.g. {@code PolicyBuilder}, still accumulating rules).
     */
    public static WildcardToken orDefault(WildcardToken declared) {
        return declared != null ? declared : DEFAULT_WILDCARD;
    }

    /**
     * meta.anyAction: absent (a {@code null} {@link PolicyDefinition.Meta#getAnyAction()})
     * -&gt; the "_ANY_" default; otherwise whatever {@link WildcardToken}
     * was declared ({@link WildcardToken.Disabled} or {@link WildcardToken.Named}).
     */
    public static WildcardToken effectiveAnyAction(PolicyDefinition.Meta meta) {
        return orDefault(meta != null ? meta.getAnyAction() : null);
    }

    /** meta.anySubject: symmetric with {@link #effectiveAnyAction} in every respect. */
    public static WildcardToken effectiveAnySubject(PolicyDefinition.Meta meta) {
        return orDefault(meta != null ? meta.getAnySubject() : null);
    }

    /** True when `value` matches `ruleValue` exactly, or `ruleValue` is the (non-disabled) wildcard token. */
    public static boolean matches(String value, String ruleValue, WildcardToken any) {
        if (value.equals(ruleValue)) return true;
        return any instanceof WildcardToken.Named named && ruleValue.equals(named.token());
    }
}
