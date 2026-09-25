package com.cptnfizzbin.keycard.conditions;

import com.cptnfizzbin.keycard.errors.PolicyLoadException;

import java.lang.System.Logger;
import java.util.Map;

/**
 * Implements the condition language and its evaluation
 * semantics. Built-in and custom {@link Operator}s share one registry and
 * are dispatched identically - this class is just the dispatch
 * loop: it looks a `$`-prefixed key up in that registry and delegates, or
 * narrows into a bare field name.
 */
public final class ConditionResolver {
    private final OperatorCatalog registry;
    private final Logger logger;
    private final OperatorContext topContext = new Ctx(true);
    private final OperatorContext nestedContext = new Ctx(false);

    public ConditionResolver() {
        this(new OperatorCatalog());
    }

    public ConditionResolver(OperatorCatalog operators) {
        this(operators, null);
    }

    /**
     * @param logger where type-issue diagnostics go; {@code null} falls back
     *   to {@code System.getLogger("Keycard")}.
     */
    public ConditionResolver(OperatorCatalog operators, Logger logger) {
        this.registry = operators != null ? operators : new OperatorCatalog();
        this.logger = logger != null ? logger : Diagnostics.DEFAULT_LOGGER;
    }

    public boolean evaluate(Object subject, Object condition) {
        return evaluate(subject, condition, true);
    }

    /** Enforces that {@code names} are all registered - built-in or custom - used to check {@code meta.operators} coverage in full at Policy construction time. */
    public void assertAllRegistered(Iterable<String> names) {
        for (String name : names) {
            if (!registry.contains(name)) {
                throw new PolicyLoadException(
                    "meta.operators declares \"" + name + "\" but no operator with that name is registered."
                );
            }
        }
    }

    private boolean evaluate(Object subject, Object condition, boolean canNarrowField) {
        if (condition instanceof Condition<?> wrapped) {
            condition = wrapped.toMap();
        }

        if (condition == null || condition instanceof String || condition instanceof Number || condition instanceof Boolean) {
            // bare-value shorthand for $eq (including explicit null - not a wildcard).
            return StringConditions.eq(subject, condition);
        }

        if (!(condition instanceof Map<?, ?> condMap)) {
            return false;
        }

        // every key MUST be evaluated and ANDed together - no key may
        // "consume" the whole object or cause sibling keys to be ignored.
        for (Map.Entry<?, ?> entry : condMap.entrySet()) {
            if (!evaluateKey(subject, String.valueOf(entry.getKey()), entry.getValue(), canNarrowField)) {
                return false;
            }
        }
        return true;
    }

    /**
     * any key starting with "$" is an operator lookup, never
     * a field name - built-in and custom operators are both resolved the
     * same way, by name, against the same registry.
     */
    private boolean evaluateKey(Object subject, String key, Object value, boolean canNarrowField) {
        OperatorContext ctx = contextFor(canNarrowField);

        if (!key.startsWith("$")) {
            return FieldAccess.check(subject, key, value, ctx);
        }

        Operator operator = registry.get(key);
        if (operator == null) return false;

        return operator.resolve(subject, value, ctx);
    }

    private OperatorContext contextFor(boolean canNarrowField) {
        return canNarrowField
            ? topContext
            : nestedContext;
    }

    final class Ctx implements OperatorContext {
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

        @Override
        public void reportTypeIssue(String operator, String message) {
            Diagnostics.logTypeIssue(logger, operator, message);
        }
    }
}
