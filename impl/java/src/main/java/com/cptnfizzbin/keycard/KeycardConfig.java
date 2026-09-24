package com.cptnfizzbin.keycard;


import com.cptnfizzbin.keycard.action.Action;
import com.cptnfizzbin.keycard.action.ActionCatalog;
import com.cptnfizzbin.keycard.conditions.OperatorCatalog;
import com.cptnfizzbin.keycard.policy.WildcardToken;
import com.cptnfizzbin.keycard.subject.Subject;
import com.cptnfizzbin.keycard.subject.SubjectCatalog;
import lombok.AccessLevel;
import lombok.Data;
import lombok.Getter;
import lombok.Setter;
import lombok.experimental.Accessors;
import org.jspecify.annotations.Nullable;

import java.lang.System.Logger;

@Data
@Accessors(fluent = true, chain = true)
public class KeycardConfig {
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
     * all. Worth paying for in development, where the goal is catching a
     * bad rule before it's reviewed. In production, a definition that
     * already passed CI doesn't need to re-prove itself on every boot.
     */
    private boolean emitMeta = true;

    /**
     * Reserved for embedding/running a policy's own shared, cross-language
     * test cases (SPEC_V0.md's {@code tests} block) - not yet wired to
     * anything in this implementation.
     */
    private boolean emitTests = false;

    /**
     * Undeclared ({@code null}) by default - only {@code PolicyBuilder}'s
     * {@code buildMeta()} ever needs to fold this into {@code
     * meta.anyAction}/{@code meta.anySubject}, and it MUST be able to tell
     * "never configured" (stays {@code null}, {@code meta.anyAction} comes
     * out undeclared too) apart from "explicitly disabled" ({@link
     * WildcardToken.Disabled}, from a bare {@code null} Action/Subject
     * argument here).
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

    public KeycardConfig anyAction(@Nullable Action action) {
        this.anyAction = action != null ? new WildcardToken.Named(action.name()) : new WildcardToken.Disabled();
        return this;
    }

    public WildcardToken anySubject() {
        return this.anySubject;
    }

    public KeycardConfig anySubject(@Nullable Subject<?, ?> subject) {
        this.anySubject = subject != null ? new WildcardToken.Named(subject.name()) : new WildcardToken.Disabled();
        return this;
    }
}
