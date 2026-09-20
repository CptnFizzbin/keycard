package com.cptnfizzbin.keycard.subject;

import com.cptnfizzbin.keycard.errors.PolicyArgumentException;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

public final class SubjectCatalog extends HashMap<String, Subject<?>> {
    private Map<String, String> namesById = new HashMap<>();

    public SubjectCatalog add(String name, Subject<?> subject) {
        this.put(name, subject);
        return this;
    }

    public SubjectCatalog add(Subject<?> subject) {
        if (subject.dynamic())
            throw new PolicyArgumentException("Dynamic subject must added to the catalog with a name");
        return this.add(subject.name(), subject);
    }

    public Optional<String> resolveName(Subject<?> subject) {
        return Optional.ofNullable(this.namesById.get(subject.id()));
    }
}

