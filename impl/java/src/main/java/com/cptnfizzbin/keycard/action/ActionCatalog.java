package com.cptnfizzbin.keycard.action;

import com.cptnfizzbin.keycard.errors.PolicyArgumentException;

import java.util.HashMap;
import java.util.Optional;

public final class ActionCatalog extends HashMap<String, Action> {
    private final HashMap<String, String> namesById = new HashMap<>();

    public ActionCatalog add(Action action) {
        if (action.dynamic())
            throw new PolicyArgumentException("Dynamic actions must added to the catalog with a name");
        return this.add(action.name(), action);
    }

    public ActionCatalog add(String name, Action action) {
        this.put(name, action);
        this.namesById.put(action.name(), name);
        return this;
    }

    public Optional<String> resolveName(Action action) {
        return Optional.ofNullable(this.namesById.get(action.id()));
    }
}
