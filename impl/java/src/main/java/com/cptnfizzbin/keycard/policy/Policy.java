package com.cptnfizzbin.keycard.policy;

import com.cptnfizzbin.keycard.KeycardConfig;
import com.cptnfizzbin.keycard.action.Action;
import com.cptnfizzbin.keycard.subject.Subject;
import com.cptnfizzbin.keycard.subject.SubjectFieldMapper;
import com.cptnfizzbin.keycard.conditions.ConditionResolver;
import com.cptnfizzbin.keycard.conditions.Operator;
import com.cptnfizzbin.keycard.errors.PolicyException;
import com.cptnfizzbin.keycard.errors.PolicyLoadException;
import com.cptnfizzbin.keycard.errors.PolicyVersionException;
import com.cptnfizzbin.keycard.version.KeyCardVersion;

import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;

public final class Policy {
    /**
     * The highest version this implementation supports natively -
     * SPEC_V1-0.md §2. PATCH never affects compatibility. Parsed from
     * {@link KeyCardVersion#KEYCARD_POLICY_VERSION}, the single source
     * of truth {@link com.cptnfizzbin.keycard.builder.PolicyBuilder#BUILDER_VERSION}
     * reads from too, so a builder can never stamp a version this same
     * implementation would then refuse to load.
     */
    public static final SemVer SUPPORTED_VERSION = SemVer.parse(KeyCardVersion.KEYCARD_POLICY_VERSION);

    private final PolicyDefinition definition;
    private final ConditionResolver resolver;
    private final KeycardConfig config;

    public Policy(PolicyDefinition definition) {
        this(definition, (Collection<Operator>) null);
    }

    /**
     * @param operators custom operators to register alongside the
     *   built-ins (SPEC_V1-0.md §7.4.12) - a single collection-based
     *   entry point shared with {@code PolicyBuilder}, so a
     *   builder-produced definition can carry its operators through
     *   consistently.
     */
    public Policy(PolicyDefinition definition, Collection<Operator> operators) {
        validateVersion(definition.getVersion());

        this.definition = definition;
        this.config = null;
        this.resolver = new ConditionResolver(operators);
        validateOperatorsRegistered(definition, resolver);
        validateRules(definition, null);
    }

    /** Advanced escape hatch: supply an already-built {@link ConditionResolver} directly. */
    public Policy(PolicyDefinition definition, ConditionResolver resolver) {
        validateVersion(definition.getVersion());

        this.definition = definition;
        this.config = null;
        this.resolver = resolver != null ? resolver : new ConditionResolver();
        validateOperatorsRegistered(definition, this.resolver);
        validateRules(definition, null);
    }

    /**
     * @param config shared, optional config also accepted by {@code
     *   PolicyBuilder} (SPEC_V1-0-0.md §3.2.2/§7.4.12 and the
     *   SubjectFieldMapper feature): {@code actions}/{@code subjects} widen
     *   the {@code meta.actions}/{@code meta.subjects} catalogs (EC-8)
     *   beyond what {@code definition.meta} itself declares; {@code
     *   operators} is registered on this Policy's resolver; {@code mapper}
     *   is consulted for a subject's fields whenever the {@link Subject}
     *   passed to {@link #can} doesn't carry its own field mapper.
     */
    public Policy(PolicyDefinition definition, KeycardConfig config) {
        validateVersion(definition.getVersion());

        this.definition = definition;
        this.config = config;
        this.resolver = new ConditionResolver(config != null ? config.getOperators() : null);
        validateOperatorsRegistered(definition, resolver);
        validateRules(definition, config);
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

    /** Alias of {@link #from(PolicyDefinition)}. */
    public static Policy fromDto(PolicyDefinition definition) {
        return from(definition);
    }

    /** Alias of {@link #from(PolicyDefinition, Collection)}. */
    public static Policy fromDto(PolicyDefinition definition, Collection<Operator> operators) {
        return from(definition, operators);
    }

    /** Alias of {@link #from(PolicyDefinition, ConditionResolver)}. */
    public static Policy fromDto(PolicyDefinition definition, ConditionResolver resolver) {
        return from(definition, resolver);
    }

    /** Alias of {@link #from(PolicyDefinition, KeycardConfig)}. */
    public static Policy fromDto(PolicyDefinition definition, KeycardConfig config) {
        return from(definition, config);
    }

    public PolicyDefinition getDefinition() {
        return toDefinition();
    }

    /** Returns the PolicyDefinition backing this policy. */
    public PolicyDefinition toDefinition() {
        return definition;
    }

    /** Alias of {@link #toDefinition()}. */
    public PolicyDefinition toDto() {
        return toDefinition();
    }

    /** A bare-type check (no instance) - EC-7/EC-9: a conditional rule can never match this. */
    public boolean can(Action<?> action, Subject<?> subject) {
        return checkPermission(action, subject);
    }

    public boolean cannot(Action<?> action, Subject<?> subject) {
        return !can(action, subject);
    }

    public void require(Action<?> action, Subject<?> subject) throws PolicyException {
        if (!can(action, subject)) {
            throw new PolicyException("Access denied: cannot " + action.getName() + " on " + subject.getName());
        }
    }

    /**
     * SPEC_V1-0.md §6: reverse scan over `rules`, returning the effect of
     * the first (i.e. most-recently-declared) rule whose action, subject,
     * and (if present) conditions all match. There is no independent
     * "allow AND NOT deny" veto and no combination of multiple matching
     * rules: exactly one rule decides the outcome, or none does and the
     * result is default deny.
     */
    private boolean checkPermission(Action<?> action, Subject<?> subject) {
        PolicyDefinition.Meta meta = definition.getMeta();
        WildcardToken anyAction = Wildcards.effectiveAnyAction(meta);
        WildcardToken anySubject = Wildcards.effectiveAnySubject(meta);
        List<PolicyDefinition.Rule> rules = definition.getRules();
        String actionName = action.getName();
        String subjectName = subject.getName();

        for (int i = rules.size() - 1; i >= 0; i--) {
            PolicyDefinition.Rule rule = rules.get(i);

            if (!Wildcards.matches(actionName, rule.getAction(), anyAction)) continue;
            if (!Wildcards.matches(subjectName, rule.getSubjectName(), anySubject)) continue;

            Map<String, Object> conditions = rule.getConditions();
            if (conditions != null) {
                // A conditional rule can never be satisfied by a bare-type/no-instance
                // check - there's no instance data for the condition to inspect (EC-7).
                if (subject.getInstance().isEmpty()) continue;
                if (!resolver.evaluate(subject.getInstance().get(), conditions, resolveFieldMapper(subject))) continue;
                return "allow".equals(rule.getEffect());
            }

            return "allow".equals(rule.getEffect());
        }

        return false; // EC-1, EC-2: default deny.
    }

    /** The subject's own field mapper (set via {@code SubjectFactory.create}) takes precedence; {@code config.getMapper()}, keyed by {@code subject.getName()}, is the fallback. */
    private SubjectFieldMapper<?> resolveFieldMapper(Subject<?> subject) {
        if (subject.getFieldMapper().isPresent()) return subject.getFieldMapper().get();
        if (config == null || config.getMapper() == null) return null;
        return config.getMapper().get(subject.getName()).orElse(null);
    }

    private static void validateVersion(String version) {
        SemVer parsed;
        try {
            parsed = SemVer.parse(version);
        } catch (RuntimeException e) {
            throw new PolicyVersionException("Invalid policy version \"" + version + "\": " + e.getMessage());
        }
        if (!parsed.isCompatibleWith(SUPPORTED_VERSION)) {
            throw new PolicyVersionException(
                "Unsupported policy version \"" + version + "\": this implementation supports up to "
                    + SUPPORTED_VERSION.major() + "." + SUPPORTED_VERSION.minor() + ".x (SPEC_V1-0.md §2)."
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
        PolicyDefinition.Meta meta = definition.getMeta();
        List<String> declared = meta != null ? meta.getOperators() : null;
        if (declared == null) return;

        resolver.assertAllRegistered(declared);
    }

    /** @param config {@code actions}/{@code subjects}, when given, widen the {@code meta.actions}/{@code meta.subjects} catalogs below (EC-8) beyond what {@code definition.meta} declares. */
    private static void validateRules(PolicyDefinition definition, KeycardConfig config) {
        PolicyDefinition.Meta meta = definition.getMeta();
        WildcardToken anyAction = Wildcards.effectiveAnyAction(meta);
        WildcardToken anySubject = Wildcards.effectiveAnySubject(meta);

        List<Action<?>> configActions = config != null ? config.getActions() : List.of();
        List<Subject<?>> configSubjects = config != null ? config.getSubjects() : List.of();

        Set<String> actionsCatalog = (meta != null && meta.getActions() != null) || !configActions.isEmpty()
            ? unionNames(meta != null ? meta.getActions() : null, configActions, Action::getNameStr)
            : null;
        Set<String> subjectsCatalog = (meta != null && meta.getSubjects() != null) || !configSubjects.isEmpty()
            ? unionNames(meta != null ? meta.getSubjects() : null, configSubjects, Subject::getName)
            : null;
        Set<String> operatorsCatalog = meta != null && meta.getOperators() != null ? new HashSet<>(meta.getOperators()) : null;

        for (PolicyDefinition.Rule rule : definition.getRules()) {
            String effect = rule.getEffect();
            String action = rule.getAction();
            String subjectName = rule.getSubjectName();
            Map<String, Object> conditions = rule.getConditions();

            if (!"allow".equals(effect) && !"deny".equals(effect)) {
                throw new PolicyLoadException(
                    "Malformed rule tuple: effect must be \"allow\" or \"deny\", got " + effect
                        + " (SPEC_V1-0.md §3.3, EC-10)."
                );
            }
            if (action == null) {
                throw new PolicyLoadException("Malformed rule tuple: action is required (SPEC_V1-0.md §3.3, EC-10).");
            }
            if (subjectName == null) {
                throw new PolicyLoadException("Malformed rule tuple: subject is required (SPEC_V1-0.md §3.3, EC-10).");
            }

            boolean isWildcardAction = anyAction instanceof WildcardToken.Named named && action.equals(named.token());
            boolean isWildcardSubject = anySubject instanceof WildcardToken.Named named && subjectName.equals(named.token());

            if (isWildcardAction && isWildcardSubject && conditions != null) {
                throw new PolicyLoadException(
                    "Rule [" + effect + ", " + action + ", " + subjectName
                        + "] is wildcarded on both the action and the subject but carries a Conditions element"
                        + " - this MUST be unconditional (SPEC_V1-0.md §6 property 5, EC-6)."
                );
            }

            if (actionsCatalog != null && !isWildcardAction && !actionsCatalog.contains(action)) {
                throw new PolicyLoadException(
                    "Rule action \"" + action + "\" is not covered by meta.actions (SPEC_V1-0.md §3.2.2, EC-8)."
                );
            }
            if (subjectsCatalog != null && !isWildcardSubject && !subjectsCatalog.contains(subjectName)) {
                throw new PolicyLoadException(
                    "Rule subject \"" + subjectName + "\" is not covered by meta.subjects (SPEC_V1-0.md §3.2.2, EC-8)."
                );
            }

            if (operatorsCatalog != null && conditions != null) {
                Set<String> used = new HashSet<>();
                ConditionResolver.collectCustomOperatorNames(conditions, used);
                for (String op : used) {
                    if (!operatorsCatalog.contains(op)) {
                        throw new PolicyLoadException(
                            "Rule uses custom operator \"" + op + "\" not covered by meta.operators (SPEC_V1-0.md §3.2.3, EC-13)."
                        );
                    }
                }
            }
        }
    }

    private static <T> Set<String> unionNames(List<String> metaNames, List<T> configItems, Function<T, String> nameOf) {
        Set<String> names = new HashSet<>();
        if (metaNames != null) names.addAll(metaNames);
        for (T item : configItems) names.add(nameOf.apply(item));
        return names;
    }
}
