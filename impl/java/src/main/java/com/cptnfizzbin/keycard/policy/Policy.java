package com.cptnfizzbin.keycard.policy;

import com.cptnfizzbin.keycard.KeycardConfig;
import com.cptnfizzbin.keycard.action.Action;
import com.cptnfizzbin.keycard.conditions.ConditionResolver;
import com.cptnfizzbin.keycard.conditions.Operator;
import com.cptnfizzbin.keycard.errors.PolicyException;
import com.cptnfizzbin.keycard.errors.PolicyLoadException;
import com.cptnfizzbin.keycard.errors.PolicyVersionException;
import com.cptnfizzbin.keycard.lib.Catalog;
import com.cptnfizzbin.keycard.lib.Logger;
import com.cptnfizzbin.keycard.subject.Subject;
import com.cptnfizzbin.keycard.subject.SubjectFieldMapper;
import com.cptnfizzbin.keycard.version.KeyCardVersion;
import org.semver4j.Semver;

import java.util.*;

public final class Policy {
    private final PolicyDefinition definition;
    private final ConditionResolver resolver;
    private final KeycardConfig config;
    /**
     * Resolves a dynamic Action/Subject's random name to its catalog key - see {@code lib/Catalog}. Empty (never null) when {@link #config} declares no keyed catalog.
     */
    private final Map<String, String> actionReverseMap;
    private final Map<String, String> subjectReverseMap;
    /**
     * Distinct raw ids already warned about via {@link #warnIfUnregisteredDynamic} - deduped per id, not per {@link #can}/{@link #cannot}/{@link #require} call.
     */
    private final Set<String> warnedDynamicIds = new HashSet<>();

    public Policy(PolicyDefinition definition) {
        this(definition, (Collection<Operator>) null);
    }

    /**
     * @param operators custom operators to register alongside the
     *                  built-ins (SPEC_V0.md §7.4.12) - a single collection-based
     *                  entry point shared with {@code PolicyBuilder}, so a
     *                  builder-produced definition can carry its operators through
     *                  consistently.
     */
    public Policy(PolicyDefinition definition, Collection<Operator> operators) {
        validateVersion(definition.version());

        this.definition = definition;
        this.config = null;
        this.actionReverseMap = Map.of();
        this.subjectReverseMap = Map.of();
        this.resolver = new ConditionResolver(operators);
        validateOperatorsRegistered(definition, resolver);
        validateRules(definition, List.of(), List.of());
    }

    /**
     * Advanced escape hatch: supply an already-built {@link ConditionResolver} directly.
     */
    public Policy(PolicyDefinition definition, ConditionResolver resolver) {
        validateVersion(definition.version());

        this.definition = definition;
        this.config = null;
        this.actionReverseMap = Map.of();
        this.subjectReverseMap = Map.of();
        this.resolver = resolver != null ? resolver : new ConditionResolver();
        validateOperatorsRegistered(definition, this.resolver);
        validateRules(definition, List.of(), List.of());
    }

    /**
     * @param config shared, optional config also accepted by {@code
     *               PolicyBuilder} (SPEC_V1-0-0.md §3.2.2/§7.4.12 and the
     *               SubjectFieldMapper feature): {@code actions}/{@code subjects} widen
     *               the {@code meta.actions}/{@code meta.subjects} catalogs (EC-8)
     *               beyond what {@code definition.meta} itself declares; a keyed
     *               {@code actionCatalog}/{@code subjectCatalog} is also a catalog
     *               resolving a dynamic (no-name) Action/Subject's random name to its
     *               key, built once here and cached (see {@code lib/Catalog});
     *               {@code operators} is registered on this Policy's resolver; {@code
     *               mapper} is consulted for a subject's fields whenever the {@link
     *               Subject} passed to {@link #can} doesn't carry its own field mapper.
     */
    public Policy(PolicyDefinition definition, KeycardConfig config) {
        validateVersion(definition.version());

        this.definition = definition;
        this.config = config;
        this.resolver = new ConditionResolver(config != null ? config.getOperators() : null);

        Catalog.Resolution actionsResolution = Catalog.build(
            config != null ? config.getActions() : null,
            config != null && config.getActionCatalog() != null ? config.getActionCatalog().toMap() : null,
            Action::getNameStr,
            "action");
        Catalog.Resolution subjectsResolution = Catalog.build(
            config != null ? config.getSubjects() : null,
            config != null && config.getSubjectCatalog() != null ? config.getSubjectCatalog().toMap() : null,
            Subject::getName,
            "subject");
        this.actionReverseMap = actionsResolution.reverseMap();
        this.subjectReverseMap = subjectsResolution.reverseMap();

        validateOperatorsRegistered(definition, resolver);
        validateRules(definition, actionsResolution.names(), subjectsResolution.names());
    }

    /**
     * Builds a Policy from an already-parsed PolicyDefinition. KeyCard itself
     * never reads or writes policy.yaml text - an application (or a test,
     * via a YAML library of its own choosing) parses the file into a plain
     * PolicyDefinition and hands it to KeyCard.
     */
    public static Policy from(PolicyDefinition definition) {
        return new Policy(definition);
    }

    public static Policy from(PolicyDefinition definition, Collection<Operator> operators) {
        return new Policy(definition, operators);
    }

    public static Policy from(PolicyDefinition definition, ConditionResolver resolver) {
        return new Policy(definition, resolver);
    }

    public static Policy from(PolicyDefinition definition, KeycardConfig config) {
        return new Policy(definition, config);
    }

    /**
     * Alias of {@link #from(PolicyDefinition)}.
     */
    public static Policy fromDto(PolicyDefinition definition) {
        return from(definition);
    }

    /**
     * Alias of {@link #from(PolicyDefinition, Collection)}.
     */
    public static Policy fromDto(PolicyDefinition definition, Collection<Operator> operators) {
        return from(definition, operators);
    }

    /**
     * Alias of {@link #from(PolicyDefinition, ConditionResolver)}.
     */
    public static Policy fromDto(PolicyDefinition definition, ConditionResolver resolver) {
        return from(definition, resolver);
    }

    /**
     * Alias of {@link #from(PolicyDefinition, KeycardConfig)}.
     */
    public static Policy fromDto(PolicyDefinition definition, KeycardConfig config) {
        return from(definition, config);
    }

    public PolicyDefinition getDefinition() {
        return toDefinition();
    }

    /**
     * Returns the PolicyDefinition backing this policy.
     */
    public PolicyDefinition toDefinition() {
        return definition;
    }

    /**
     * Alias of {@link #toDefinition()}.
     */
    public PolicyDefinition toDto() {
        return toDefinition();
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
            String actionName = Catalog.resolveName(actionReverseMap, action.getNameStr());
            String subjectName = Catalog.resolveName(subjectReverseMap, subject.getName());
            throw new PolicyException("Access denied: cannot " + actionName + " on " + subjectName);
        }
    }

    /**
     * SPEC_V0.md §6: reverse scan over `rules`, returning the effect of
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
        List<PolicyDefinition.Rule> rules = definition.rules();
        warnIfUnregisteredDynamic(action.isDynamic(), action.getNameStr(), actionReverseMap, "Action", "Action.create()/ActionFactory.create()");
        warnIfUnregisteredDynamic(subject.isDynamic(), subject.getName(), subjectReverseMap, "Subject", "Subject.create()/SubjectFactory.create()");
        String actionName = Catalog.resolveName(actionReverseMap, action.getNameStr());
        String subjectName = Catalog.resolveName(subjectReverseMap, subject.getName());

        for (int i = rules.size() - 1; i >= 0; i--) {
            PolicyDefinition.Rule rule = rules.get(i);

            if (!Wildcards.matches(actionName, rule.action(), anyAction)) continue;
            if (!Wildcards.matches(subjectName, rule.subjectName(), anySubject)) continue;

            Map<String, Object> conditions = rule.conditions();
            if (conditions != null) {
                // A conditional rule can never be satisfied by a bare-type/no-instance
                // check - there's no instance data for the condition to inspect (EC-7).
                if (subject.getInstance().isEmpty()) continue;
                if (!resolver.evaluate(subject.getInstance().get(), conditions, resolveFieldMapper(subject))) continue;
                return "allow".equals(rule.effect());
            }

            return "allow".equals(rule.effect());
        }

        return false; // EC-1, EC-2: default deny.
    }

    /**
     * The subject's own field mapper (set via {@code SubjectFactory.create}) takes precedence; {@code config.getMapper()}, keyed by the subject's resolved catalog name, is the fallback.
     */
    private SubjectFieldMapper<?> resolveFieldMapper(Subject<?> subject) {
        if (subject.getFieldMapper().isPresent()) return subject.getFieldMapper().get();
        if (config == null || config.getMapper() == null) return null;
        return config.getMapper().get(Catalog.resolveName(subjectReverseMap, subject.getName())).orElse(null);
    }

    /**
     * A dynamic (no-name) Action/Subject never registered in any catalog
     * reachable from this Policy can't resolve to a real name - it falls
     * through to default-deny like any other non-match (unless a wildcard
     * rule catches it), but that's silent otherwise, so warn once per
     * distinct id rather than once per {@link #can}/{@link #cannot}/
     * {@link #require} call.
     */
    private void warnIfUnregisteredDynamic(boolean dynamic, String rawName, Map<String, String> reverseMap, String kind, String factory) {
        if (!dynamic || reverseMap.containsKey(rawName) || warnedDynamicIds.contains(rawName)) return;
        warnedDynamicIds.add(rawName);
        Logger logger = config != null && config.getLogger() != null ? config.getLogger() : Logger.NO_OP;
        logger.warn(
            kind + " created via " + factory + " with no name was checked but never registered in any"
                + " KeycardConfig catalog reachable from this Policy - it can never match a non-wildcard rule."
        );
    }

    private static void validateVersion(String version) {
        Semver parsed;
        try {
            parsed = Semver.coerce(version);
        } catch (RuntimeException e) {
            throw new PolicyVersionException("Invalid policy version \"" + version + "\": " + e.getMessage());
        }
        if (!parsed.satisfies(KeyCardVersion.KEYCARD_POLICY_SUPPORTED_VERSIONS)) {
            throw new PolicyVersionException(
                "Unsupported policy version \"" + version + "\": this implementation supports " + KeyCardVersion.KEYCARD_POLICY_SUPPORTED_VERSIONS
            );
        }
    }

    /**
     * §3.2.3, EC-15 (promoted): when {@code meta.operators} is declared,
     * every name it lists MUST already be registered on this Policy - built
     * -in or custom - checked once here at construction time, regardless of
     * whether any rule actually reaches that operator during evaluation.
     * This replaces the previous behavior of deferring an unregistered-but
     * -cataloged name to a runtime-only diagnostic.
     */
    private static void validateOperatorsRegistered(PolicyDefinition definition, ConditionResolver resolver) {
        PolicyDefinition.Meta meta = definition.meta();
        List<String> declared = meta != null ? meta.operators() : null;
        if (declared == null) return;

        resolver.assertAllRegistered(declared);
    }

    /**
     * @param configActionNames/@param configSubjectNames resolved catalog names (see {@code lib/Catalog}) that, when given, widen the {@code meta.actions}/{@code meta.subjects} catalogs below (EC-8) beyond what {@code definition.meta} declares.
     */
    private static void validateRules(PolicyDefinition definition, List<String> configActionNames, List<String> configSubjectNames) {
        PolicyDefinition.Meta meta = definition.meta();
        WildcardToken anyAction = Wildcards.effectiveAnyAction(meta);
        WildcardToken anySubject = Wildcards.effectiveAnySubject(meta);

        Set<String> actionsCatalog = (meta != null && meta.actions() != null) || !configActionNames.isEmpty()
            ? unionNames(meta != null ? meta.actions() : null, configActionNames)
            : null;
        Set<String> subjectsCatalog = (meta != null && meta.subjects() != null) || !configSubjectNames.isEmpty()
            ? unionNames(meta != null ? meta.subjects() : null, configSubjectNames)
            : null;
        Set<String> operatorsCatalog = meta != null && meta.operators() != null ? new HashSet<>(meta.operators()) : null;

        for (PolicyDefinition.Rule rule : definition.getRules()) {
            String effect = rule.effect();
            String action = rule.action();
            String subjectName = rule.subjectName();
            Map<String, Object> conditions = rule.conditions();

            if (!"allow".equals(effect) && !"deny".equals(effect)) {
                throw new PolicyLoadException(
                    "Malformed rule tuple: effect must be \"allow\" or \"deny\", got " + effect
                        + " (SPEC_V0.md §3.3, EC-10)."
                );
            }
            if (action == null) {
                throw new PolicyLoadException("Malformed rule tuple: action is required (SPEC_V0.md §3.3, EC-10).");
            }
            if (subjectName == null) {
                throw new PolicyLoadException("Malformed rule tuple: subject is required (SPEC_V0.md §3.3, EC-10).");
            }

            boolean isWildcardAction = anyAction instanceof WildcardToken.Named named && action.equals(named.token());
            boolean isWildcardSubject = anySubject instanceof WildcardToken.Named named && subjectName.equals(named.token());

            if (isWildcardAction && isWildcardSubject && conditions != null) {
                throw new PolicyLoadException(
                    "Rule [" + effect + ", " + action + ", " + subjectName
                        + "] is wildcarded on both the action and the subject but carries a Conditions element"
                        + " - this MUST be unconditional (SPEC_V0.md §6 property 5, EC-6)."
                );
            }

            if (actionsCatalog != null && !isWildcardAction && !actionsCatalog.contains(action)) {
                throw new PolicyLoadException(
                    "Rule action \"" + action + "\" is not covered by meta.actions (SPEC_V0.md §3.2.2, EC-8)."
                );
            }
            if (subjectsCatalog != null && !isWildcardSubject && !subjectsCatalog.contains(subjectName)) {
                throw new PolicyLoadException(
                    "Rule subject \"" + subjectName + "\" is not covered by meta.subjects (SPEC_V0.md §3.2.2, EC-8)."
                );
            }

            if (operatorsCatalog != null && conditions != null) {
                Set<String> used = new HashSet<>();
                ConditionResolver.collectCustomOperatorNames(conditions, used);
                for (String op : used) {
                    if (!operatorsCatalog.contains(op)) {
                        throw new PolicyLoadException(
                            "Rule uses custom operator \"" + op + "\" not covered by meta.operators (SPEC_V0.md §3.2.3, EC-13)."
                        );
                    }
                }
            }
        }
    }

    private static Set<String> unionNames(List<String> metaNames, List<String> configNames) {
        Set<String> names = new HashSet<>();
        if (metaNames != null) names.addAll(metaNames);
        names.addAll(configNames);
        return names;
    }
}
