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
            throw new PolicyArgumentException("Dynamic actions must be added to the catalog with a name");
        return this.add(action.name(), action);
    }

    public ActionCatalog add(String name, Action action) {
        this.put(name, action);
        return this;
    }

    /**
     * Registers {@code action} under the explicit key {@code name} and
     * returns {@code action} itself - so a dynamic Action can be declared
     * and registered in one line: {@code static Action Create = catalog.set("create", new Action());}
     */
    public Action set(String name, Action action) {
        this.put(name, action);
        return action;
    }

    /**
     * Registers {@code action} under its own name and returns {@code
     * action} itself - the single-arg counterpart of {@link #set(String, Action)}
     * for a non-dynamic Action that already carries its own name.
     */
    public Action set(Action action) {
        if (action.dynamic())
            throw new PolicyArgumentException("Dynamic actions must be added to the catalog with a name");
        return this.set(action.name(), action);
    }
}
