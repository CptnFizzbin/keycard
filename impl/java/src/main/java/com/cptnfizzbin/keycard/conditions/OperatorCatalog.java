package com.cptnfizzbin.keycard.conditions;

import com.cptnfizzbin.keycard.errors.PolicyLoadException;

import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;

/**
 * Every operator a {@link ConditionResolver} can dispatch to - the built-ins,
 * registered on construction, plus any custom ones added after. Wraps its map
 * rather than extending one, so a registration can't bypass the name checks
 * in {@link #add} and a built-in can't be removed.
 */
public final class OperatorCatalog {
    /** Every operator name this catalog registers out of the box - the single source of truth for "is this name built-in". */
    public static final Set<String> BUILTIN_NAMES = DefaultOperators.NAMES;

    private final Map<String, Operator> operators = new LinkedHashMap<>();

    public OperatorCatalog() {
        this.addAll(DefaultOperators.ALL);
    }

    /**
     * An operator name MUST start with "$" - any other key in a condition is
     * a field name, so an operator registered without one could never be
     * dispatched. A name collision - built-in or custom - MUST throw
     * immediately rather than silently overwriting the previous registration.
     */
    public OperatorCatalog add(Operator operator) {
        String name = operator.name();
        if (name == null || !name.startsWith("$") || name.length() < 2) {
            throw new PolicyLoadException(
                "Invalid operator name \"" + name + "\": operator names MUST start with \"$\" (e.g. \"$hasRole\")."
            );
        }
        if (operators.containsKey(name)) {
            throw new PolicyLoadException(
                "Duplicate operator \"" + name + "\": an operator with this name is already registered"
                    + " (built-in or custom) - operator names MUST be unique."
            );
        }
        operators.put(name, operator);
        return this;
    }

    public OperatorCatalog addAll(Collection<Operator> operators) {
        operators.forEach(this::add);
        return this;
    }

    /**
     * Registers a flat {@link ConditionOperator} under {@code name} and
     * returns it - adapting it into a full {@link Operator} internally, the
     * same way a duplicate name is rejected for either.
     */
    public ConditionOperator set(String name, ConditionOperator operator) {
        this.add(Operator.of(name, (subject, value, ctx) -> operator.resolve(subject, value)));
        return operator;
    }

    /** The operator registered under {@code name}, or {@code null}. */
    public Operator get(String name) {
        return operators.get(name);
    }

    public boolean contains(String name) {
        return operators.containsKey(name);
    }

    /** Every registered operator name, built-ins first, in registration order. */
    public Set<String> names() {
        return Collections.unmodifiableSet(operators.keySet());
    }

    /** Every registered operator name that isn't one of the built-ins - what {@code meta.operators} derives from usage. */
    public Set<String> customNames() {
        Set<String> names = new LinkedHashSet<>(operators.keySet());
        names.removeAll(BUILTIN_NAMES);
        return names;
    }
}
