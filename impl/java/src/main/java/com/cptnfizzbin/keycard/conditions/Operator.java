package com.cptnfizzbin.keycard.conditions;

/**
 * A single `$`-prefixed condition operator (SPEC_V1-0.md §7.4) - built-in
 * or custom. Both are the exact same type and are registered, looked up,
 * and dispatched identically (§7.4.12): {@link DefaultOperators} supplies
 * one {@code Operator} per built-in, and a host application supplies its
 * own for a custom `$op` the same way, via whatever collection it passes
 * to {@code Policy}/{@code PolicyBuilder}/{@link ConditionResolver}. There
 * is deliberately no separate "custom checker" type - unifying the two
 * closes the capability gap a flat {@code (subject, value) -> boolean}
 * checker had: every {@code Operator}, custom ones included, receives an
 * {@link OperatorContext} letting it recurse into the condition language.
 */
public interface Operator {
    /** The `$`-prefixed name this operator is registered under (e.g. `"$eq"`, `"$hasRole"`). */
    String name();

    /**
     * Evaluates this operator against `subject`/`value`. `ctx` lets the
     * implementation recurse into the condition language via {@link
     * OperatorContext#resolveSubcondition} - exactly what a custom
     * operator needs to implement something like `$and`/`$or` itself.
     */
    boolean resolve(Object subject, Object value, OperatorContext ctx);

    /** Builds an {@code Operator} from a name and a {@link Resolver} - the common case, for both built-ins and custom operators alike. */
    static Operator of(String name, Resolver resolver) {
        return new Operator() {
            @Override
            public String name() {
                return name;
            }

            @Override
            public boolean resolve(Object subject, Object value, OperatorContext ctx) {
                return resolver.resolve(subject, value, ctx);
            }
        };
    }

    @FunctionalInterface
    interface Resolver {
        boolean resolve(Object subject, Object value, OperatorContext ctx);
    }
}
