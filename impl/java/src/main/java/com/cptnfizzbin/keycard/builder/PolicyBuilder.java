package com.cptnfizzbin.keycard.builder;

import com.cptnfizzbin.keycard.KeycardConfig;
import com.cptnfizzbin.keycard.action.Action;
import com.cptnfizzbin.keycard.conditions.Condition;
import com.cptnfizzbin.keycard.errors.PolicyArgumentException;
import com.cptnfizzbin.keycard.lib.Catalog;
import com.cptnfizzbin.keycard.policy.Policy;
import com.cptnfizzbin.keycard.policy.PolicyDefinition;
import com.cptnfizzbin.keycard.policy.Wildcards;
import com.cptnfizzbin.keycard.policy.WildcardToken;
import com.cptnfizzbin.keycard.subject.Subject;
import lombok.NonNull;
import lombok.val;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashSet;
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
    private final Set<String> actionsUsed = new LinkedHashSet<>();
    private final Set<String> subjectsUsed = new LinkedHashSet<>();

    @NotNull
    private final KeycardConfig config;

    private final Catalog.Resolution actionResolution;
    private final Catalog.Resolution subjectResolution;

    public PolicyBuilder() {
        this(new KeycardConfig());
    }

    public PolicyBuilder(@NonNull KeycardConfig config) {
        this.config = config;
        this.actionResolution = Catalog.build(null, config.actions(), Action::name, "action");
        this.subjectResolution = Catalog.build(null, config.subjects(), Subject::name, "subject");
    }

    public PolicyBuilder allow(Action action, Subject<?, ?> subject) {
        return allow(action, subject, null);
    }

    public PolicyBuilder allow(Collection<Action> actions, Subject<?, ?> subject) {
        actions.forEach(action -> this.allow(action, subject));
        return this;
    }

    public <S> PolicyBuilder allow(Collection<Action> actions, Subject<S, ?> subject, Condition<S> condition) {
        actions.forEach(action -> this.allow(action, subject, condition));
        return this;
    }

    public <S> PolicyBuilder allow(Action action, Subject<S, ?> subject, Condition<S> condition) {
        return addRule("allow", action, subject, condition);
    }

    public PolicyBuilder deny(Action action, Subject<?, ?> subject) {
        return deny(action, subject, null);
    }

    public PolicyBuilder deny(Iterable<Action> actions, Subject<?, ?> subject) {
        actions.forEach(action -> this.deny(action, subject));
        return this;
    }

    public <S> PolicyBuilder deny(Action action, Subject<S, ?> subject, Condition<S> conditions) {
        this.addRule("deny", action, subject, conditions);
        return this;
    }

    public Policy build() {
        return new Policy(buildDef(), config);
    }

    public PolicyDefinition buildDef() {
        return new PolicyDefinition()
            // A copy, so allow()/deny() calls made after this can't reach
            // back into an already-built definition (or a Policy made from it).
            .rules(new ArrayList<>(this.rules))
            .meta(config.emitMeta() ? buildMeta() : null);
    }

    /**
     * derives `actions`/`subjects`/`operators` from what was actually used/registered, plus whatever `config`'s catalogs additionally declare - see the class doc.
     */
    private PolicyDefinition.Meta buildMeta() {
        Set<String> actions = new LinkedHashSet<>(actionsUsed);
        actions.addAll(actionResolution.names());

        Set<String> subjects = new LinkedHashSet<>(subjectsUsed);
        subjects.addAll(subjectResolution.names());

        PolicyDefinition.Meta meta = new PolicyDefinition.Meta();

        meta.actions(List.copyOf(actions));
        meta.subjects(List.copyOf(subjects));
        meta.operators(List.copyOf(config.operators().customNames()));

        meta.anyAction(config.anyAction());
        meta.anySubject(config.anySubject());

        return meta;
    }

    private <S> PolicyBuilder addRule(String effect, Action action, Subject<S, ?> subject, @Nullable Condition<S> condition) {
        if (config.emitMeta()) {
            if (action.dynamic() && !actionResolution.reverseMap().containsKey(action.name())) {
                throw new PolicyArgumentException(
                    "This Action was created with no name and must be registered as a catalog value on the KeycardConfig handed to this PolicyBuilder before use."
                );
            }
            if (subject.dynamic() && !subjectResolution.reverseMap().containsKey(subject.name())) {
                throw new PolicyArgumentException(
                    "This Subject was created with no name and must be registered as a catalog value on the KeycardConfig handed to this PolicyBuilder before use."
                );
            }
        }

        String actionName = Catalog.resolveName(actionResolution.reverseMap(), action.name());
        String subjectName = Catalog.resolveName(subjectResolution.reverseMap(), subject.name());

        if (condition != null) {
            // SPEC_V0.md property 5, EC-6: a rule wildcarded on both the
            // action and the subject MUST NOT carry a Conditions element -
            // caught here immediately, rather than waiting for eventual
            // construction (new Policy(...)) to catch it.
            WildcardToken anyAction = Wildcards.orDefault(config.anyAction());
            WildcardToken anySubject = Wildcards.orDefault(config.anySubject());

            boolean isWildcardAction = anyAction instanceof WildcardToken.Named named && actionName.equals(named.token());
            boolean isWildcardSubject = anySubject instanceof WildcardToken.Named named && subjectName.equals(named.token());

            if (isWildcardAction && isWildcardSubject) {
                throw new PolicyArgumentException(
                    "A rule wildcarded on both the action (\"" + actionName + "\") and the subject (\"" + subjectName
                        + "\") MUST NOT carry a Conditions element (SPEC_V0.md property 5, EC-6)."
                );
            }
        }

        actionsUsed.add(actionName);
        subjectsUsed.add(subjectName);

        val conditionMap = condition != null ? condition.toMap() : null;

        this.rules.add(new PolicyDefinition.Rule(effect, actionName, subjectName, conditionMap));

        return this;
    }
}
