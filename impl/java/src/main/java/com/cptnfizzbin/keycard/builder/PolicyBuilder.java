package com.cptnfizzbin.keycard.builder;

import com.cptnfizzbin.keycard.KeycardConfig;
import com.cptnfizzbin.keycard.action.Action;
import com.cptnfizzbin.keycard.conditions.Condition;
import com.cptnfizzbin.keycard.errors.PolicyException;
import com.cptnfizzbin.keycard.policy.Policy;
import com.cptnfizzbin.keycard.policy.PolicyDefinition;
import com.cptnfizzbin.keycard.subject.Subject;
import lombok.NonNull;
import lombok.val;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Set;

/**
 * Builds a {@link PolicyDefinition} rule by rule. {@code meta.actions}/
 * {@code meta.subjects}/{@code meta.operators} are never supplied
 * directly by default - {@link #buildDef()} fills them in automatically
 * from what {@link #allow}/{@link #deny} actually used and what {@code
 * operators} actually registered, so there's no separately hand-maintained
 * catalog to keep in sync by hand. The only meta fields a caller ever needs
 * to declare explicitly are the wildcard tokens themselves -
 * nothing about them can be inferred from usage. {@link KeycardConfig}'s
 * {@code actions}/{@code subjects} (optional) declare additional vocabulary
 * up front, folded in alongside whatever usage derives.
 */
public class PolicyBuilder {
    private final List<PolicyDefinition.Rule> rules = new ArrayList<>();

    @NotNull
    private final KeycardConfig config;

    public PolicyBuilder() {
        this(new KeycardConfig());
    }

    public PolicyBuilder(@NonNull KeycardConfig config) {
        this.config = config;
    }

    public PolicyBuilder allow(Action action, Subject<?> subject) {
        return allow(action, subject, null);
    }

    public PolicyBuilder allow(Collection<Action> actions, Subject<?> subject) {
        actions.forEach(action -> this.allow(action, subject));
        return this;
    }

    public <S> PolicyBuilder allow(Collection<Action> actions, Subject<S> subject, Condition<S> condition) {
        actions.forEach(action -> this.allow(action, subject, condition));
        return this;
    }

    public <S> PolicyBuilder allow(Action action, Subject<S> subject, Condition<S> condition) {
        return addRule("allow", action, subject, condition);
    }

    public PolicyBuilder deny(Action action, Subject<?> subject) {
        return deny(action, subject, null);
    }

    public PolicyBuilder deny(Iterable<Action> actions, Subject<?> subject) {
        actions.forEach(action -> this.deny(action, subject));
        return this;
    }

    public <S> PolicyBuilder deny(Action action, Subject<S> subject, Condition<S> conditions) {
        this.addRule("deny", action, subject, conditions);
        return this;
    }

    public Policy build() {
        return new Policy(buildDef(), config);
    }

    public PolicyDefinition buildDef() {
        return new PolicyDefinition()
            .rules(this.rules)
            .meta(buildMeta());
    }

    /**
     * derives `actions`/`subjects`/`operators` from what was actually used/registered, plus whatever `config`'s catalogs (list and/or keyed) additionally declare - see the class doc.
     */
    private PolicyDefinition.Meta buildMeta() {
        Set<String> actions = config.actions().keySet();
        Set<String> subjects = config.actions().keySet();
        Set<String> operators = config.operators().keySet();

        PolicyDefinition.Meta meta = new PolicyDefinition.Meta();

        meta.actions(List.copyOf(actions));
        meta.subjects(List.copyOf(subjects));
        meta.operators(List.copyOf(operators));

        meta.anyAction(config.anyAction());
        meta.anySubject(config.anySubject());

        return meta;
    }

    private <S> PolicyBuilder addRule(String effect, Action action, Subject<S> subject, @Nullable Condition<S> condition) {
        val actionName = this.config.actions().resolveName(action).orElseGet(() -> {
            if (action.dynamic()) throw new PolicyException("Dynamic action not registered in catalog");
            this.config.actions().add(action);
            return action.id();
        });

        val subjectName = this.config.subjects().resolveName(subject).orElseGet(() -> {
            if (subject.dynamic()) throw new PolicyException("Dynamic subject not registered in catalog");
            this.config.subjects().add(subject);
            return subject.name();
        });

        val conditionMap = condition != null ? condition.toMap() : null;

        this.rules.add(new PolicyDefinition.Rule(effect, actionName, subjectName, conditionMap));
        
        return this;
    }
}
