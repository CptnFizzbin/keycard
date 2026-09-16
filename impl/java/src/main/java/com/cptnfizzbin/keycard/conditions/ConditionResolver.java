package com.cptnfizzbin.keycard.conditions;

import com.cptnfizzbin.keycard.errors.PolicyLoadException;

import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Implements SPEC_V1-0-0.md §7: the condition language and its evaluation
 * semantics. Built-in and custom {@link Operator}s share one registry and
 * are dispatched identically (§7.4.12) - this class is just the dispatch
 * loop: it looks a `$`-prefixed key up in that registry and delegates, or
 * narrows into a bare field name.
 */
public final class ConditionResolver {

    /** Every `$`-prefixed name {@link DefaultOperators} supplies natively - the single source of truth for "is this name built-in". */
    public static final Set<String> BUILTIN_OPERATORS = names(DefaultOperators.ALL);

    private final Map<String, Operator> registry;
    private final OperatorContext topContext = new Ctx(true);
    private final OperatorContext nestedContext = new Ctx(false);

    public ConditionResolver() {
        this(null);
    }

    /**
     * @param operators custom operators to register alongside {@link DefaultOperators} (§7.4.12) -
     *   built-in and custom operators share this one collection-based
     *   entry point. Constructing this with a name collision (a custom
     *   operator sharing a `$name` with a built-in, or with another
     *   operator in `operators`) MUST throw a {@link PolicyLoadException}
     *   immediately - never a silent overwrite (SPEC_V1-0-0.md §3.2.3, EC-16).
     */
    public ConditionResolver(Collection<Operator> operators) {
        this.registry = buildRegistry(operators);
    }

    /**
     * §3.2.3, EC-15 (promoted): throws if any name in {@code names} isn't
     * registered on this resolver - built-in or custom. Used by {@code
     * Policy} to enforce {@code meta.operators} registration coverage in
     * full at construction time, regardless of whether any rule actually
     * reaches a given operator during evaluation.
     */
    public void assertAllRegistered(Collection<String> names) {
        for (String name : names) {
            if (!registry.containsKey(name)) {
                throw new PolicyLoadException(
                    "meta.operators declares \"" + name + "\" but no operator with that name is registered"
                        + " (built-in or custom) (SPEC_V1-0-0.md §3.2.3, EC-15)."
                );
            }
        }
    }

    /**
     * Recursively collects every non-built-in, {@code $}-prefixed operator
     * name used anywhere in a Conditions tree - used by {@code Policy} to
     * enforce {@code meta.operators} coverage at construction time (§3.2.3,
     * EC-13).
     */
    public static void collectCustomOperatorNames(Object condition, Set<String> out) {
        if (!(condition instanceof Map)) return;

        Map<?, ?> map = (Map<?, ?>) condition;
        for (Map.Entry<?, ?> entry : map.entrySet()) {
            String key = String.valueOf(entry.getKey());
            Object value = entry.getValue();

            if (key.startsWith("$")) {
                if (!BUILTIN_OPERATORS.contains(key)) out.add(key);
                if (key.equals("$or") || key.equals("$and")) {
                    if (value instanceof List) {
                        for (Object c : (List<?>) value) collectCustomOperatorNames(c, out);
                    }
                } else if (key.equals("$not")) {
                    collectCustomOperatorNames(value, out);
                } else if (key.equals("$field") && value instanceof List && ((List<?>) value).size() == 2) {
                    collectCustomOperatorNames(((List<?>) value).get(1), out);
                }
            } else {
                collectCustomOperatorNames(value, out);
            }
        }
    }

    /**
     * §7.1: {@code evaluate} always returns a boolean and SHOULD NOT throw
     * for any well-formed condition, regardless of what the subject is.
     */
    public boolean evaluate(Object subject, Object condition) {
        return evaluate(subject, condition, true);
    }

    /**
     * §7.4.10: {@code canNarrowField} tracks whether a field condition
     * (bare-key or {@code $field}) is still allowed to narrow at this point
     * in the tree - {@code true} at the root and while only recursing
     * through non-narrowing combinators ($and/$or/$not), {@code false} once
     * a field condition has already narrowed once, since v1 supports only
     * one level of field access.
     */
    private boolean evaluate(Object subject, Object condition, boolean canNarrowField) {
        if (condition == null || condition instanceof String || condition instanceof Number || condition instanceof Boolean) {
            // §7.2: bare-value shorthand for $eq (including explicit null - §7.3, not a wildcard).
            return StringConditions.eq(subject, condition);
        }

        if (!(condition instanceof Map)) {
            return false;
        }

        Map<?, ?> condMap = (Map<?, ?>) condition;
        // §7.5: every key MUST be evaluated and ANDed together - no key may
        // "consume" the whole object or cause sibling keys to be ignored.
        for (Map.Entry<?, ?> entry : condMap.entrySet()) {
            if (!evaluateKey(subject, String.valueOf(entry.getKey()), entry.getValue(), canNarrowField)) {
                return false;
            }
        }
        return true;
    }

    /**
     * §7.4.12, §7.5: any key starting with "$" is an operator lookup, never
     * a field name - built-in and custom operators are both resolved the
     * same way, by name, against the same registry.
     */
    private boolean evaluateKey(Object subject, String key, Object value, boolean canNarrowField) {
        OperatorContext ctx = canNarrowField ? topContext : nestedContext;

        if (!key.startsWith("$")) {
            return FieldAccess.check(subject, key, value, ctx);
        }

        Operator operator = registry.get(key);
        if (operator == null) {
            // §7.4.12, EC-13: an operator with no checker registered (built-in
            // or custom) MUST evaluate to false - never a no-op true, and
            // never treated as a field name. Not itself a required §7.1
            // diagnostic - and, unlike before, a cataloged-but-unregistered
            // name (EC-15) can no longer even reach this branch: `Policy`
            // now enforces meta.operators registration at construction time,
            // so any name still unregistered here was never cataloged.
            return false;
        }

        return operator.resolve(subject, value, ctx);
    }

    /**
     * Backs {@link OperatorContext} for one fixed {@code canNarrowField}
     * state - {@link #topContext} (narrowing still allowed) and {@link
     * #nestedContext} (already narrowed once) are the only two instances
     * ever needed, since v1 supports exactly one level of field access.
     */
    private final class Ctx implements OperatorContext {
        private final boolean canNarrowField;

        Ctx(boolean canNarrowField) {
            this.canNarrowField = canNarrowField;
        }

        @Override
        public boolean resolveSubcondition(Object subject, Object condition) {
            return evaluate(subject, condition, canNarrowField);
        }

        @Override
        public boolean resolveFieldSubcondition(Object subject, Object condition) {
            return evaluate(subject, condition, false);
        }

        @Override
        public boolean canNarrowField() {
            return canNarrowField;
        }
    }

    private static Map<String, Operator> buildRegistry(Collection<Operator> custom) {
        Map<String, Operator> map = new LinkedHashMap<>();
        for (Operator op : DefaultOperators.ALL) {
            map.put(op.name(), op);
        }
        if (custom != null) {
            for (Operator op : custom) {
                if (map.containsKey(op.name())) {
                    throw new PolicyLoadException(
                        "Duplicate operator \"" + op.name() + "\": an operator with this name is already registered"
                            + " (built-in or custom) - operator names MUST be unique (SPEC_V1-0-0.md §3.2.3, EC-16)."
                    );
                }
                map.put(op.name(), op);
            }
        }
        return Map.copyOf(map);
    }

    private static Set<String> names(Collection<Operator> operators) {
        Set<String> names = new LinkedHashSet<>();
        for (Operator op : operators) {
            names.add(op.name());
        }
        return Set.copyOf(names);
    }
}
