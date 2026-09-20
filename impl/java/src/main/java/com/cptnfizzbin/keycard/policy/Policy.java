package com.cptnfizzbin.keycard.policy;

import com.cptnfizzbin.keycard.KeycardConfig;
import com.cptnfizzbin.keycard.action.Action;
import com.cptnfizzbin.keycard.conditions.ConditionResolver;
import com.cptnfizzbin.keycard.errors.PolicyException;
import com.cptnfizzbin.keycard.errors.PolicyVersionException;
import com.cptnfizzbin.keycard.subject.Subject;
import com.cptnfizzbin.keycard.version.KeyCardVersion;
import lombok.Getter;
import lombok.val;
import org.semver4j.Semver;

import java.util.List;
import java.util.Map;
import java.util.Optional;

public final class Policy {
    @Getter
    private final PolicyDefinition definition;

    private final ConditionResolver resolver;
    private final KeycardConfig config;

    public Policy(PolicyDefinition definition) {
        this(definition, new KeycardConfig());
    }

    public Policy(PolicyDefinition definition, KeycardConfig config) {
        validateVersion(definition.version());
        this.definition = definition;
        this.config = config;
        this.resolver = new ConditionResolver(config.operators());
    }

    /**
     * A bare-type check (no instance) - EC-7/EC-9: a conditional rule can never match this.
     */
    public boolean can(Action action, Subject<?> subject) {
        return checkPermission(action, subject);
    }

    public boolean cannot(Action action, Subject<?> subject) {
        return !can(action, subject);
    }

    public void require(Action action, Subject<?> subject) throws PolicyException {
        if (!can(action, subject)) {
            String actionName = config.actions().resolveName(action).orElse(action.id());
            String subjectName = config.subjects().resolveName(subject).orElse(subject.id());
            throw new PolicyException("Access denied: cannot " + actionName + " on " + subjectName);
        }
    }

    /**
     * SPEC_V1-0.md: reverse scan over `rules`, returning the effect of
     * the first (i.e. most-recently-declared) rule whose action, subject,
     * and (if present) conditions all match. There is no independent
     * "allow AND NOT deny" veto and no combination of multiple matching
     * rules: exactly one rule decides the outcome, or none does and the
     * result is default deny.
     */
    private boolean checkPermission(Action action, Subject<?> subject) {
        PolicyDefinition.Meta meta = definition.meta();
        WildcardToken anyAction = Wildcards.effectiveAnyAction(meta);
        WildcardToken anySubject = Wildcards.effectiveAnySubject(meta);
        List<PolicyDefinition.Rule> rules = definition.getRules();

        String actionName = this.config.actions().resolveName(action).orElseGet(action::id);
        String subjectName = this.config.subjects().resolveName(subject).orElseGet(subject::id);

        for (int i = rules.size() - 1; i >= 0; i--) {
            PolicyDefinition.Rule rule = rules.get(i);

            if (!Wildcards.matches(actionName, rule.subjectName(), anyAction)) continue;
            if (!Wildcards.matches(subjectName, rule.subjectName(), anySubject)) continue;

            Map<String, Object> conditions = rule.conditions();
            if (conditions != null) {
                // A conditional rule can never be satisfied by a bare-type/no-instance
                // check - there's no instance data for the condition to inspect (EC-7).
                if (subject.claims().isEmpty()) continue;
                if (!resolver.evaluate(subject.claims().get(), conditions)) continue;
                return "allow".equals(rule.effect());
            }

            return "allow".equals(rule.effect());
        }

        return false;
    }

    private static void validateVersion(String version) {
        val supported = Optional.ofNullable(Semver.coerce(version))
            .orElseThrow(() -> new PolicyVersionException("Invalid version " + version))
            .satisfies(KeyCardVersion.KEYCARD_POLICY_SUPPORTED_VERSIONS);

        if (!supported) {
            throw new PolicyVersionException(
                "Unsupported policy version \"" + version + "\": this implementation supports " + KeyCardVersion.KEYCARD_POLICY_SUPPORTED_VERSIONS
            );
        }
    }
}
