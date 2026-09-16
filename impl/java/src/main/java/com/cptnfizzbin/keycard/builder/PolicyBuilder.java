package com.cptnfizzbin.keycard.builder;

import com.cptnfizzbin.keycard.KeycardConfig;
import com.cptnfizzbin.keycard.action.Action;
import com.cptnfizzbin.keycard.subject.Subject;
import com.cptnfizzbin.keycard.conditions.Operator;
import com.cptnfizzbin.keycard.errors.PolicyArgumentException;
import com.cptnfizzbin.keycard.policy.Policy;
import com.cptnfizzbin.keycard.policy.PolicyDefinition;
import com.cptnfizzbin.keycard.policy.Wildcards;
import com.cptnfizzbin.keycard.policy.WildcardToken;
import com.cptnfizzbin.keycard.version.KeyCardVersion;

import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Builds a {@link PolicyDefinition} rule by rule. {@code meta.actions}/
 * {@code meta.subjects}/{@code meta.operators} are never supplied
 * directly by default - {@link #buildDef()} fills them in automatically
 * from what {@link #allow}/{@link #deny} actually used and what {@code
 * operators} actually registered, so there's no separately hand-maintained
 * catalog to keep in sync by hand. The only meta fields a caller ever needs
 * to declare explicitly are the wildcard tokens themselves (§3.2.1) -
 * nothing about them can be inferred from usage. {@link KeycardConfig}'s
 * {@code actions}/{@code subjects} (optional) declare additional vocabulary
 * up front, folded in alongside whatever usage derives.
 */
public final class PolicyBuilder {
    /** The v1 SemVer this builder implements - stamped onto every buildDef() output, per SPEC_V1-0-0.md §2. Single-sourced from {@link KeyCardVersion}, alongside {@link Policy#SUPPORTED_VERSION}, so the two can never drift apart. */
    public static final String BUILDER_VERSION = KeyCardVersion.KEYCARD_POLICY_VERSION;

    private final List<PolicyDefinition.Rule> rules = new ArrayList<>();
    private final Set<String> actionsUsed = new LinkedHashSet<>();
    private final Set<String> subjectsUsed = new LinkedHashSet<>();
    /** Null means "not declared" - the §3.2.1 "_ANY_" default applies. Never {@link WildcardToken.of}'s output of a bare no-arg construction. */
    private final WildcardToken anyAction;
    private final WildcardToken anySubject;
    private final Collection<Operator> operators;
    private final KeycardConfig config;

    public PolicyBuilder() {
        this.anyAction = null;
        this.anySubject = null;
        this.operators = null;
        this.config = null;
    }

    public PolicyBuilder(Collection<Operator> operators) {
        this.anyAction = null;
        this.anySubject = null;
        this.operators = operators;
        this.config = null;
    }

    /**
     * @param anyAction declares meta.anyAction explicitly - dispatched per
     *   {@link WildcardToken#of} (a {@link String} names the token; {@code
     *   null}/{@code false} disables it; anything else throws {@code
     *   PolicyLoadException} immediately, before a single rule is added).
     *   Pass this constructor only to declare something other than the
     *   spec default ("_ANY_") - {@code meta.actions}/{@code subjects}/
     *   {@code operators} are never set here; see the class doc.
     */
    public PolicyBuilder(Object anyAction, Object anySubject) {
        this(anyAction, anySubject, (Collection<Operator>) null);
    }

    public PolicyBuilder(Object anyAction, Object anySubject, Collection<Operator> operators) {
        this.anyAction = WildcardToken.of(anyAction);
        this.anySubject = WildcardToken.of(anySubject);
        this.operators = operators;
        this.config = null;
    }

    /**
     * @param config shared, optional config also accepted by {@link
     *   Policy}: {@code actions}/{@code subjects} are folded into {@code
     *   meta.actions}/{@code meta.subjects} alongside whatever {@link
     *   #allow}/{@link #deny} actually used; {@code operators} is
     *   registered the same way the {@link Collection} constructors'
     *   {@code operators} is; {@code mapper} is carried through to the
     *   built {@link Policy} unchanged.
     */
    public PolicyBuilder(KeycardConfig config) {
        // Mirrors the no-arg constructor's wildcard handling (leaves both
        // "not declared", so the §3.2.1 "_ANY_" default applies) - NOT the
        // (Object, Object, ...) constructors', where an explicit `null`
        // instead disables the wildcard via WildcardToken.of.
        this.anyAction = null;
        this.anySubject = null;
        this.config = config;
        this.operators = config != null ? config.getOperators() : null;
    }

    public PolicyBuilder(Object anyAction, Object anySubject, KeycardConfig config) {
        this.anyAction = WildcardToken.of(anyAction);
        this.anySubject = WildcardToken.of(anySubject);
        this.config = config;
        this.operators = config != null ? config.getOperators() : null;
    }

    public PolicyBuilder allow(Action<?> action, Subject<?> subject) {
        return allow(action, subject, null);
    }

    public PolicyBuilder allow(Action<?> action, Subject<?> subject, Map<String, Object> conditions) {
        return addRule("allow", action.getName(), subject.getName(), conditions);
    }

    public PolicyBuilder deny(Action<?> action, Subject<?> subject) {
        return deny(action, subject, null);
    }

    public PolicyBuilder deny(Action<?> action, Subject<?> subject, Map<String, Object> conditions) {
        return addRule("deny", action.getName(), subject.getName(), conditions);
    }

    public Policy build() {
        return config != null ? new Policy(buildDef(), config) : new Policy(buildDef(), operators);
    }

    public PolicyDefinition buildDef() {
        return new PolicyDefinition(BUILDER_VERSION, null, null, buildMeta(), rules);
    }

    /** §3.2.2/§3.2.3: derives `actions`/`subjects`/`operators` from what was actually used/registered, plus whatever `config.getActions()`/`config.getSubjects()` additionally declare - see the class doc. */
    private PolicyDefinition.Meta buildMeta() {
        Set<String> actions = new LinkedHashSet<>(actionsUsed);
        Set<String> subjects = new LinkedHashSet<>(subjectsUsed);
        if (config != null) {
            for (Action<?> action : config.getActions()) actions.add(action.getNameStr());
            for (Subject<?> subject : config.getSubjects()) subjects.add(subject.getName());
        }

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

    private PolicyBuilder addRule(String effect, String action, String subjectName, Map<String, Object> conditions) {
        if (conditions != null) {
            // SPEC_V1-0-0.md §6 property 5, EC-6: a rule wildcarded on both
            // the action and the subject MUST NOT carry a Conditions element
            // - the builder MUST catch this immediately, rather than waiting
            // for eventual construction (Policy.from) to catch it.
            WildcardToken effAnyAction = Wildcards.orDefault(anyAction);
            WildcardToken effAnySubject = Wildcards.orDefault(anySubject);
            boolean actionIsWildcard = effAnyAction instanceof WildcardToken.Named named && action.equals(named.token());
            boolean subjectIsWildcard = effAnySubject instanceof WildcardToken.Named named && subjectName.equals(named.token());
            if (actionIsWildcard && subjectIsWildcard) {
                throw new PolicyArgumentException("rules with any action and any subject cannot be conditional");
            }
        }

        actionsUsed.add(action);
        subjectsUsed.add(subjectName);
        rules.add(new PolicyDefinition.Rule(effect, action, subjectName, conditions));
        return this;
    }
}
