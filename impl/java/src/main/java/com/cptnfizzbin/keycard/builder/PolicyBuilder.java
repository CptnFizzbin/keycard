package com.cptnfizzbin.keycard.builder;

import com.cptnfizzbin.keycard.KeycardConfig;
import com.cptnfizzbin.keycard.action.Action;
import com.cptnfizzbin.keycard.conditions.Condition;
import com.cptnfizzbin.keycard.errors.PolicyException;
import com.cptnfizzbin.keycard.subject.Subject;
import com.cptnfizzbin.keycard.conditions.Operator;
import com.cptnfizzbin.keycard.policy.Policy;
import com.cptnfizzbin.keycard.policy.PolicyDefinition;
import com.cptnfizzbin.keycard.version.KeyCardVersion;
import lombok.val;
import org.jetbrains.annotations.Nullable;

import java.util.*;

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
    /**
     * The v1 SemVer this builder implements - stamped onto every buildDef() output, per SPEC_V1-0.md Single-sourced from {@link KeyCardVersion}, alongside {@link Policy#SUPPORTED_VERSION}, so the two can never drift apart.
     */
    public static final String BUILDER_VERSION = KeyCardVersion.KEYCARD_POLICY_VERSION;

    private final List<PolicyDefinition.Rule> rules = new ArrayList<>();

    private final KeycardConfig config;

    public PolicyBuilder() {
        this(new KeycardConfig());
    }

    public PolicyBuilder(KeycardConfig config) {
        this.config = config;
    }

    public PolicyBuilder allow(Action action, Subject<?> subject) {
        return allow(action, subject, null);
    }

    public PolicyBuilder allow(Iterable<Action> actions, Subject<?> subject) {
        actions.forEach(action -> this.allow(action, subject));
        return this;
    }

    public <S> PolicyBuilder allow(Iterable<Action> actions, Subject<?> subject, @Nullable Condition<S> condition) {
        actions.forEach(action -> this.allow(action, subject, condition));
        return this;
    }

    public <S> PolicyBuilder allow(Action action, Subject<S> subject, @Nullable Condition<S> condition) {
        return addRule("allow", action, subject, condition);
    }

    public PolicyBuilder deny(Action action, Subject<?> subject) {
        return deny(action, subject, null);
    }

    public PolicyBuilder deny(Iterable<Action> actions, Subject<?> subject) {
        actions.forEach(action -> this.deny(action, subject));
        return this;
    }

    public PolicyBuilder deny(Action action, Subject<?> subject, Map<String, Object> conditions) {
        this.addRule("deny", action, subject, conditions);
        return this;
    }

    public Policy build() {
        return config != null ? new Policy(buildDef(), config) : new Policy(buildDef(), operators);
    }

    public PolicyDefinition buildDef() {
        return new PolicyDefinition(BUILDER_VERSION, null, null, buildMeta(), rules);
    }

    /**
     * derives `actions`/`subjects`/`operators` from what was actually used/registered, plus whatever `config`'s catalogs (list and/or keyed) additionally declare - see the class doc.
     */
    private PolicyDefinition.Meta buildMeta() {
        Set<String> actions = new LinkedHashSet<>(actionsUsed);
        actions.addAll(configActionNames);
        Set<String> subjects = new LinkedHashSet<>(subjectsUsed);
        subjects.addAll(configSubjectNames);

        PolicyDefinition.Meta.Builder builder = PolicyDefinition.Meta.builder()
            .actions(List.copyOf(actions))
            .subjects(List.copyOf(subjects));

        if (anyAction != null) builder.anyAction(anyAction);
        if (anySubject != null) builder.anySubject(anySubject);

        if (operators != null && !operators.isEmpty()) {
            List<String> names = new ArrayList<>();
            for (Operator op : operators) names.add(op.name());
            builder.operators(names);
        }

        return builder.build();
    }

    private <S> PolicyBuilder addRule(String effect, Action action, Subject<S> subject, @Nullable Condition<S> condition) {
        val actionName = this.config.actions().getNameById(action.id()).orElseGet(() -> {
            if (action.dynamic()) throw new PolicyException("Dynamic action not registered in catalog");
            this.config.actions().add(action);
            return action.id();
        });

        val subjectName = this.config.subjects().getNameById(subject.id()).orElseGet(() -> {
            if (subject.dynamic()) throw new PolicyException("Dynamic subject not registered in catalog");
            this.config.actions().add(subject);
            return subject.id();
        });

        val conditionMap = condition != null ? condition.toMap() : null;

        this.rules.add(new PolicyDefinition.Rule(effect, actionName, subjectName, conditionMap));
    }
}
