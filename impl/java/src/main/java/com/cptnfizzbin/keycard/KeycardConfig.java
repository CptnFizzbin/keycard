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

    public KeycardConfig anySubject(@Nullable Subject<?> subject) {
        this.anySubject = subject != null ? new WildcardToken.Named(subject.name()) : new WildcardToken.Disabled();
        return this;
    }
}
