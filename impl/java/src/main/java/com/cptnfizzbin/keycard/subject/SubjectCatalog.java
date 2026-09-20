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
public final class SubjectCatalog extends LinkedHashMap<String, Subject<?>> {
    public SubjectCatalog add(String name, Subject<?> subject) {
        this.put(name, subject);
        return this;
    }

    public SubjectCatalog add(Subject<?> subject) {
        if (subject.dynamic())
            throw new PolicyArgumentException("Dynamic subject must added to the catalog with a name");
        return this.add(subject.name(), subject);
    }
}
