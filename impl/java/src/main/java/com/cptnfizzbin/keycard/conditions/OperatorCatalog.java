package com.cptnfizzbin.keycard.conditions;

import java.util.Collection;
import java.util.HashMap;

public final class OperatorCatalog extends HashMap<String, Operator> {
    public OperatorCatalog() {
        this.addAll(DefaultOperators.ALL);
    }

    public OperatorCatalog add(Operator operator) {
        this.put(operator.name(), operator);
        return this;
    }

    public OperatorCatalog addAll(Collection<Operator> operators) {
        operators.forEach(this::add);
        return this;
    }
}
