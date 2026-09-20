package com.cptnfizzbin.keycard.conditions;

import java.util.Map;

/**
 * Implements SPEC_V0.md: the condition language and its evaluation
 * semantics. Built-in and custom {@link Operator}s share one registry and
 * are dispatched identically - this class is just the dispatch
 * loop: it looks a `$`-prefixed key up in that registry and delegates, or
 * narrows into a bare field name.
 */
public final class ConditionResolver {
    private final OperatorCatalog registry;
    private final OperatorContext topContext = new Ctx(true);
    private final OperatorContext nestedContext = new Ctx(false);

    public ConditionResolver() {
        this(null);
    }

    public ConditionResolver(OperatorCatalog operators) {
        this.registry = operators;
    }

    public boolean evaluate(Object subject, Object condition) {
        return evaluate(subject, condition, true);
    }

    private boolean evaluate(Object subject, Object condition, boolean canNarrowField) {
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
    }
}
