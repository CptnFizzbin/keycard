package com.cptnfizzbin.keycard.subject;

import com.cptnfizzbin.keycard.errors.PolicyArgumentException;

import java.util.LinkedHashMap;

/**
 * A plain {@code name -> Subject} catalog - both a self-keyed vocabulary
 * declaration ({@link #add(Subject)}, keyed by the Subject's own name) and
 * an explicitly-keyed catalog ({@link #add(String, Subject)}, required for
 * a dynamic Subject) share this one map. {@code PolicyBuilder}/{@code
 * Policy} resolve it into the actual {@code id -> catalog key} reverse
 * lookup once, at construction, via {@code lib.Catalog}.
 */
public final class SubjectCatalog extends LinkedHashMap<String, Subject<?, ?>> {
    public SubjectCatalog add(String name, Subject<?, ?> subject) {
        this.put(name, subject);
        return this;
    }

    public SubjectCatalog add(Subject<?, ?> subject) {
        if (subject.dynamic())
            throw new PolicyArgumentException("Dynamic subject must be added to the catalog with a name");
        return this.add(subject.name(), subject);
    }

    /**
     * Registers {@code subject} under the explicit key {@code name} and
     * returns {@code subject} itself (its real, possibly subclassed type) -
     * so a dedicated Subject subclass can be declared and registered in one
     * line: {@code static ProjectSubject Project = catalog.set("project", new ProjectSubject());}
     */
    public <S extends Subject<?, ?>> S set(String name, S subject) {
        this.put(name, subject);
        return subject;
    }

    /**
     * Registers {@code subject} under its own name and returns {@code
     * subject} itself - the single-arg counterpart of {@link #set(String, Subject)}
     * for a non-dynamic Subject that already carries its own name.
     */
    public <S extends Subject<?, ?>> S set(S subject) {
        if (subject.dynamic())
            throw new PolicyArgumentException("Dynamic subject must be added to the catalog with a name");
        return this.set(subject.name(), subject);
    }
}
