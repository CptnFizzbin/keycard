package com.cptnfizzbin.keycard.subject;

import com.cptnfizzbin.keycard.errors.PolicyArgumentException;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * A {@code name -> Subject} catalog - both a self-keyed vocabulary
 * declaration ({@link #add(Subject)}, keyed by the Subject's own name) and
 * an explicitly-keyed catalog ({@link #add(String, Subject)}, required for
 * a dynamic Subject) share this one map. {@code PolicyBuilder}/{@code
 * Policy} resolve it into the actual {@code id -> catalog key} reverse
 * lookup once, at construction, via {@code lib.Catalog}.
 * <p>
 * Wraps its map rather than extending one, so every registration goes
 * through the checks below - {@link #asMap()} is a read-only view.
 */
public final class SubjectCatalog {
    private final Map<String, Subject<?, ?>> entries = new LinkedHashMap<>();

    /**
     * Registering the same Subject under the same key again is a no-op; a
     * different Subject under an already-used key throws rather than
     * silently replacing the first.
     */
    public SubjectCatalog add(String name, Subject<?, ?> subject) {
        Subject<?, ?> existing = entries.putIfAbsent(name, subject);
        if (existing != null && existing != subject) {
            throw new PolicyArgumentException(
                "SubjectCatalog already has a different Subject registered under \"" + name + "\"."
            );
        }
        return this;
    }

    public SubjectCatalog add(Subject<?, ?> subject) {
        if (subject.dynamic())
            throw new PolicyArgumentException("Dynamic subjects must be added to the catalog with a name");
        return this.add(subject.name(), subject);
    }

    /**
     * Registers {@code subject} under the explicit key {@code name} and
     * returns {@code subject} itself (its real, possibly subclassed type) -
     * so a dedicated Subject subclass can be declared and registered in one
     * line: {@code static ProjectSubject Project = catalog.set("project", new ProjectSubject());}
     */
    public <S extends Subject<?, ?>> S set(String name, S subject) {
        this.add(name, subject);
        return subject;
    }

    /**
     * Registers {@code subject} under its own name and returns {@code
     * subject} itself - the single-arg counterpart of {@link #set(String, Subject)}
     * for a non-dynamic Subject that already carries its own name.
     */
    public <S extends Subject<?, ?>> S set(S subject) {
        this.add(subject);
        return subject;
    }

    /** The Subject registered under {@code name}, or {@code null}. */
    public Subject<?, ?> get(String name) {
        return entries.get(name);
    }

    public boolean contains(String name) {
        return entries.containsKey(name);
    }

    /** A read-only, insertion-ordered view of every {@code key -> Subject} registration. */
    public Map<String, Subject<?, ?>> asMap() {
        return Collections.unmodifiableMap(entries);
    }
}
