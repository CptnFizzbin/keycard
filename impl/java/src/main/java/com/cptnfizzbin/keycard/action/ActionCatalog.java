package com.cptnfizzbin.keycard.action;

import com.cptnfizzbin.keycard.errors.PolicyArgumentException;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * A {@code name -> Action} catalog - both a self-keyed vocabulary
 * declaration ({@link #add(Action)}, keyed by the Action's own name) and an
 * explicitly-keyed catalog ({@link #add(String, Action)}, required for a
 * dynamic Action) share this one map. {@code PolicyBuilder}/{@code Policy}
 * resolve it into the actual {@code id -> catalog key} reverse lookup once,
 * at construction, via {@code lib.Catalog}.
 * <p>
 * Wraps its map rather than extending one, so every registration goes
 * through the checks below - {@link #asMap()} is a read-only view.
 */
public final class ActionCatalog {
    private final Map<String, Action> entries = new LinkedHashMap<>();

    public ActionCatalog add(Action action) {
        if (action.dynamic())
            throw new PolicyArgumentException("Dynamic actions must be added to the catalog with a name");
        return this.add(action.name(), action);
    }

    /**
     * Registering the same Action under the same key again is a no-op; a
     * different Action under an already-used key throws rather than silently
     * replacing the first.
     */
    public ActionCatalog add(String name, Action action) {
        Action existing = entries.putIfAbsent(name, action);
        if (existing != null && existing != action) {
            throw new PolicyArgumentException(
                "ActionCatalog already has a different Action registered under \"" + name + "\"."
            );
        }
        return this;
    }

    /**
     * Registers {@code action} under the explicit key {@code name} and
     * returns {@code action} itself - so a dynamic Action can be declared
     * and registered in one line: {@code static Action Create = catalog.set("create", new Action());}
     */
    public Action set(String name, Action action) {
        this.add(name, action);
        return action;
    }

    /**
     * Registers {@code action} under its own name and returns {@code
     * action} itself - the single-arg counterpart of {@link #set(String, Action)}
     * for a non-dynamic Action that already carries its own name.
     */
    public Action set(Action action) {
        this.add(action);
        return action;
    }

    /** The Action registered under {@code name}, or {@code null}. */
    public Action get(String name) {
        return entries.get(name);
    }

    public boolean contains(String name) {
        return entries.containsKey(name);
    }

    /** A read-only, insertion-ordered view of every {@code key -> Action} registration. */
    public Map<String, Action> asMap() {
        return Collections.unmodifiableMap(entries);
    }
}
