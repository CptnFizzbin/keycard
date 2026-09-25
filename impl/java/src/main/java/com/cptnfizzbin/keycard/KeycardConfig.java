package com.cptnfizzbin.keycard;


import com.cptnfizzbin.keycard.action.Action;
import com.cptnfizzbin.keycard.action.ActionCatalog;
import com.cptnfizzbin.keycard.builder.PolicyBuilder;
import com.cptnfizzbin.keycard.conditions.OperatorCatalog;
import com.cptnfizzbin.keycard.policy.WildcardToken;
import com.cptnfizzbin.keycard.subject.Subject;
import com.cptnfizzbin.keycard.subject.SubjectCatalog;
import com.cptnfizzbin.keycard.errors.PolicyArgumentException;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.Setter;
import lombok.experimental.Accessors;

import java.lang.System.Logger;

/**
 * Mutable, shared configuration for {@link PolicyBuilder} and {@code Policy}.
 * Deliberately has no {@code equals}/{@code hashCode}: it holds mutable
 * catalogs and a logger, so identity is the only meaningful equality.
 */
@Getter
@Setter
@Accessors(fluent = true, chain = true)
public class KeycardConfig {
    /** Where type-issue diagnostics from condition evaluation are reported (at {@code ERROR}). */
    private Logger logger = System.getLogger("Keycard");

    private ActionCatalog actions = new ActionCatalog();
    private SubjectCatalog subjects = new SubjectCatalog();

    private OperatorCatalog operators = new OperatorCatalog();

    /**
     * Gates two things together: the eager, fail-fast checks {@link
     * com.cptnfizzbin.keycard.builder.PolicyBuilder}/{@code Policy} do at
     * construction beyond what's needed to actually resolve/evaluate a rule
     * (a dynamic Action/Subject used but never registered on this catalog,
     * a {@code Policy}'s loaded {@code PolicyDefinition} not actually
     * satisfying its own declared {@code meta.actions}/{@code
     * meta.subjects}/{@code meta.operators}) - and whether {@link
     * PolicyBuilder#buildDef()} attaches the derived {@code meta} block at
     * all. Structural rule checks (a malformed rule tuple, a conditional
     * both-sides-wildcarded rule) always run regardless. Worth paying for in development, where the goal is catching a
     * bad rule before it's reviewed. In production, a definition that
     * already passed CI doesn't need to re-prove itself on every boot.
     */
    private boolean emitMeta = true;

    /**
     * Reserved for embedding/running a policy's own shared, cross-language
     * test cases (the {@code tests} block) - not yet wired to
     * anything in this implementation.
     */
    private boolean emitTests = false;

    /**
     * Undeclared ({@code null}) by default - only {@code PolicyBuilder}'s
     * {@code buildMeta()} ever needs to fold this into {@code
     * meta.anyAction}/{@code meta.anySubject}, and it MUST be able to tell
     * "never configured" (stays {@code null}, {@code meta.anyAction} comes
     * out undeclared too) apart from "explicitly disabled" ({@link
     * WildcardToken.Disabled}, from {@link #disableAnyAction()}).
     */
    @Getter(AccessLevel.NONE)
    @Setter(AccessLevel.NONE)
    private WildcardToken anyAction = null;

    @Getter(AccessLevel.NONE)
    @Setter(AccessLevel.NONE)
    private WildcardToken anySubject = null;

    public WildcardToken anyAction() {
        return this.anyAction;
    }

    /** Declares {@code action}'s name as the action wildcard token. */
    public KeycardConfig anyAction(Action action) {
        if (action == null) {
            throw new PolicyArgumentException("anyAction(null): use disableAnyAction() to disable the action wildcard.");
        }
        this.anyAction = new WildcardToken.Named(action.name());
        return this;
    }

    /** Disables the action wildcard - no action name, including {@code "_ANY_"}, has special meaning. */
    public KeycardConfig disableAnyAction() {
        this.anyAction = WildcardToken.DISABLED;
        return this;
    }

    public WildcardToken anySubject() {
        return this.anySubject;
    }

    /** Declares {@code subject}'s name as the subject wildcard token. */
    public KeycardConfig anySubject(Subject<?, ?> subject) {
        if (subject == null) {
            throw new PolicyArgumentException("anySubject(null): use disableAnySubject() to disable the subject wildcard.");
        }
        this.anySubject = new WildcardToken.Named(subject.name());
        return this;
    }

    /** Disables the subject wildcard - no subject name, including {@code "_ANY_"}, has special meaning. */
    public KeycardConfig disableAnySubject() {
        this.anySubject = WildcardToken.DISABLED;
        return this;
    }
}
