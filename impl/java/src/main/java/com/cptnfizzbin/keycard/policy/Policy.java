package com.cptnfizzbin.keycard.policy;

import com.cptnfizzbin.keycard.KeycardConfig;
import com.cptnfizzbin.keycard.action.Action;
import com.cptnfizzbin.keycard.conditions.ConditionResolver;
import com.cptnfizzbin.keycard.conditions.OperatorCatalog;
import com.cptnfizzbin.keycard.errors.PolicyException;
import com.cptnfizzbin.keycard.errors.PolicyLoadException;
import com.cptnfizzbin.keycard.errors.PolicyVersionException;
import com.cptnfizzbin.keycard.lib.Catalog;
import com.cptnfizzbin.keycard.subject.Subject;
import com.cptnfizzbin.keycard.version.KeyCardVersion;
import lombok.Getter;
import lombok.val;
import org.semver4j.Semver;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

public final class Policy {
    @Getter
    private final PolicyDefinition definition;

    private final ConditionResolver resolver;
    private final KeycardConfig config;
    private final Map<String, String> actionReverseMap;
    private final Map<String, String> subjectReverseMap;

    // Snapshotted at construction, so evaluation only ever sees the rules and
    // wildcard tokens that were validated here - later mutation of the
    // (mutable) PolicyDefinition can't bypass validation.
    private final List<PolicyDefinition.Rule> rules;
    private final WildcardToken anyAction;
    private final WildcardToken anySubject;

    public Policy(PolicyDefinition definition) {
        this(definition, new KeycardConfig());
    }

    public Policy(PolicyDefinition definition, KeycardConfig config) {
        validateVersion(definition.version());
        this.definition = definition;
        this.config = config;
        this.resolver = new ConditionResolver(config.operators());

        Catalog.Resolution actions = Catalog.build(null, config.actions(), Action::name, "action");
        Catalog.Resolution subjects = Catalog.build(null, config.subjects(), Subject::name, "subject");
        this.actionReverseMap = actions.reverseMap();
        this.subjectReverseMap = subjects.reverseMap();

        this.rules = definition.getRules();
        this.anyAction = Wildcards.effectiveAnyAction(definition.meta());
        this.anySubject = Wildcards.effectiveAnySubject(definition.meta());

        // Structural validity is what evaluation itself relies on, so it's
        // checked regardless of emitMeta - only the catalog checks are gated.
        validateRuleShapes(rules, anyAction, anySubject);
        if (config.emitMeta()) {
            validateOperatorsRegistered(definition, resolver);
            validateRuleCatalogs(definition, rules, anyAction, anySubject, actions.names(), subjects.names());
        }
    }

    /**
     * A bare-type check (no instance): a conditional rule can never match this.
     */
    public boolean can(Action action, Subject<?, ?> subject) {
        return checkPermission(action, subject);
    }

    public boolean cannot(Action action, Subject<?, ?> subject) {
        return !can(action, subject);
    }

    public void require(Action action, Subject<?, ?> subject) throws PolicyException {
        if (!can(action, subject)) {
            String actionName = Catalog.resolveName(actionReverseMap, action.name());
            String subjectName = Catalog.resolveName(subjectReverseMap, subject.name());
            throw new PolicyException("Access denied: cannot " + actionName + " on " + subjectName);
        }
    }

    /**
     * Reverse scan over `rules`, returning the effect of
     * the first (i.e. most-recently-declared) rule whose action, subject,
     * and (if present) conditions all match. There is no independent
     * "allow AND NOT deny" veto and no combination of multiple matching
     * rules: exactly one rule decides the outcome, or none does and the
     * result is default deny.
     */
    private boolean checkPermission(Action action, Subject<?, ?> subject) {
        String actionName = Catalog.resolveName(actionReverseMap, action.name());
        String subjectName = Catalog.resolveName(subjectReverseMap, subject.name());

        for (int i = rules.size() - 1; i >= 0; i--) {
            PolicyDefinition.Rule rule = rules.get(i);

            if (!Wildcards.matches(actionName, rule.action(), anyAction)) continue;
            if (!Wildcards.matches(subjectName, rule.subjectName(), anySubject)) continue;

            Map<String, Object> conditions = rule.conditions();
            if (conditions != null) {
                // A conditional rule can never be satisfied by a bare-type/no-instance
                // check - there's no instance data for the condition to inspect.
                if (subject.claims().isEmpty()) continue;
                if (!resolver.evaluate(subject.claims().get(), conditions)) continue;
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

    /**
     * when `meta.operators` is declared, every
     * name it lists MUST already be registered on this Policy - built-in or
     * custom - checked once here when loading a policy, regardless of
     * whether any rule actually reaches that operator during evaluation.
     */
    private static void validateOperatorsRegistered(PolicyDefinition definition, ConditionResolver resolver) {
        PolicyDefinition.Meta meta = definition.meta();
        List<String> declared = meta != null ? meta.operators() : null;
        if (declared == null) return;

        resolver.assertAllRegistered(declared);
    }

    /**
     * Malformed rule tuples and conditional both-sides-wildcarded rules -
     * always checked, since evaluation can't proceed safely past either.
     */
    private static void validateRuleShapes(List<PolicyDefinition.Rule> rules, WildcardToken anyAction, WildcardToken anySubject) {
        for (PolicyDefinition.Rule rule : rules) {
            if (!"allow".equals(rule.effect()) && !"deny".equals(rule.effect())) {
                throw new PolicyLoadException(
                    "Malformed rule tuple: effect must be \"allow\" or \"deny\", got " + rule.effect() + "."
                );
            }
            if (rule.action() == null) {
                throw new PolicyLoadException("Malformed rule tuple: action must be a string, got null.");
            }
            if (rule.subjectName() == null) {
                throw new PolicyLoadException("Malformed rule tuple: subject must be a string, got null.");
            }

            if (isWildcard(anyAction, rule.action()) && isWildcard(anySubject, rule.subjectName()) && rule.conditions() != null) {
                throw new PolicyLoadException(
                    "Rule [" + rule.effect() + ", " + rule.action() + ", " + rule.subjectName()
                        + "] is wildcarded on both the action and the subject but carries a Conditions element - this MUST be unconditional."
                );
            }
        }
    }

    private static boolean isWildcard(WildcardToken any, String value) {
        return any instanceof WildcardToken.Named named && value.equals(named.token());
    }

    /**
     * @param configActionNames resolved catalog names (see {@code lib.Catalog}) that, when given, widen the `meta.actions` catalog below beyond what `definition.meta` declares.
     * @param configSubjectNames resolved catalog names (see {@code lib.Catalog}) that, when given, widen the `meta.subjects` catalog below beyond what `definition.meta` declares.
     */
    private static void validateRuleCatalogs(
        PolicyDefinition definition,
        List<PolicyDefinition.Rule> rules,
        WildcardToken anyAction,
        WildcardToken anySubject,
        List<String> configActionNames,
        List<String> configSubjectNames
    ) {
        PolicyDefinition.Meta meta = definition.meta();

        List<String> metaActions = meta != null ? meta.actions() : null;
        List<String> metaSubjects = meta != null ? meta.subjects() : null;
        List<String> metaOperators = meta != null ? meta.operators() : null;

        Set<String> actionsCatalog = null;
        if ((metaActions != null && !metaActions.isEmpty()) || !configActionNames.isEmpty()) {
            actionsCatalog = new LinkedHashSet<>();
            if (metaActions != null) actionsCatalog.addAll(metaActions);
            actionsCatalog.addAll(configActionNames);
        }

        Set<String> subjectsCatalog = null;
        if ((metaSubjects != null && !metaSubjects.isEmpty()) || !configSubjectNames.isEmpty()) {
            subjectsCatalog = new LinkedHashSet<>();
            if (metaSubjects != null) subjectsCatalog.addAll(metaSubjects);
            subjectsCatalog.addAll(configSubjectNames);
        }

        Set<String> operatorsCatalog = metaOperators != null ? new LinkedHashSet<>(metaOperators) : null;

        for (PolicyDefinition.Rule rule : rules) {
            boolean isWildcardAction = isWildcard(anyAction, rule.action());
            boolean isWildcardSubject = isWildcard(anySubject, rule.subjectName());

            if (actionsCatalog != null && !isWildcardAction && !actionsCatalog.contains(rule.action())) {
                throw new PolicyLoadException("Rule action \"" + rule.action() + "\" is not covered by meta.actions.");
            }
            if (subjectsCatalog != null && !isWildcardSubject && !subjectsCatalog.contains(rule.subjectName())) {
                throw new PolicyLoadException("Rule subject \"" + rule.subjectName() + "\" is not covered by meta.subjects.");
            }

            if (operatorsCatalog != null && rule.conditions() != null) {
                Set<String> used = new LinkedHashSet<>();
                collectCustomOperators(rule.conditions(), used);
                for (String op : used) {
                    if (!operatorsCatalog.contains(op)) {
                        throw new PolicyLoadException("Rule uses custom operator \"" + op + "\" not covered by meta.operators.");
                    }
                }
            }
        }
    }

    /**
     * Recursively collects every non-built-in, `$`-prefixed operator name
     * used anywhere in a Conditions tree - used to enforce `meta.operators`
     * coverage when loading a policy.
     */
    private static void collectCustomOperators(Object condition, Set<String> out) {
        if (!(condition instanceof Map<?, ?> map)) return;

        for (Map.Entry<?, ?> entry : map.entrySet()) {
            String key = String.valueOf(entry.getKey());
            Object value = entry.getValue();

            if (key.startsWith("$")) {
                if (key.equals("$or") || key.equals("$and")) {
                    if (value instanceof List<?> list) {
                        for (Object c : list) collectCustomOperators(c, out);
                    }
                } else if (key.equals("$not")) {
                    collectCustomOperators(value, out);
                } else if (key.equals("$field") && value instanceof List<?> list && list.size() == 2) {
                    collectCustomOperators(list.get(1), out);
                } else if (!OperatorCatalog.BUILTIN_NAMES.contains(key)) {
                    out.add(key);
                }
            } else {
                collectCustomOperators(value, out);
            }
        }
    }
}
