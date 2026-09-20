package com.cptnfizzbin.keycard.action;

import com.cptnfizzbin.keycard.errors.PolicyArgumentException;

import java.util.LinkedHashMap;

/**
 * A plain {@code name -> Action} catalog - both a self-keyed vocabulary
 * declaration ({@link #add(Action)}, keyed by the Action's own name) and an
 * explicitly-keyed catalog ({@link #add(String, Action)}, required for a
 * dynamic Action) share this one map. {@code PolicyBuilder}/{@code Policy}
 * resolve it into the actual {@code id -> catalog key} reverse lookup once,
 * at construction, via {@code lib.Catalog}.
 */
public final class ActionCatalog extends LinkedHashMap<String, Action> {
    public ActionCatalog add(Action action) {
        if (action.dynamic())
            throw new PolicyArgumentException("Dynamic actions must added to the catalog with a name");
        return this.add(action.name(), action);
    }

    public ActionCatalog add(String name, Action action) {
        this.put(name, action);
        return this;
    }
}
