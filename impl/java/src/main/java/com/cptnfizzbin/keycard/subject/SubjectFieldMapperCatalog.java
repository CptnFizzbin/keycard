package com.cptnfizzbin.keycard.subject;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;

/**
 * A {@link SubjectFieldMapper} registered per subject name - the
 * catalog-level counterpart to attaching one directly via
 * {@link SubjectFactory#create(String, SubjectFieldMapper)}. Handed to
 * {@code Policy}/{@code PolicyBuilder} via {@code KeycardConfig#getMapper()}
 * so field mappers can be registered centrally, for subjects created
 * without one inline.
 */
public final class SubjectFieldMapperCatalog {
    private final Map<String, SubjectFieldMapper<?>> mappers;

    private SubjectFieldMapperCatalog(Map<String, SubjectFieldMapper<?>> mappers) {
        this.mappers = new LinkedHashMap<>(mappers);
    }

    public static Builder builder() {
        return new Builder();
    }

    public Optional<SubjectFieldMapper<?>> get(String subjectName) {
        return Optional.ofNullable(mappers.get(subjectName));
    }

    public static final class Builder {
        private final Map<String, SubjectFieldMapper<?>> mappers = new LinkedHashMap<>();

        private Builder() {}

        public <T> Builder register(String subjectName, SubjectFieldMapper<T> mapper) {
            mappers.put(subjectName, mapper);
            return this;
        }

        public SubjectFieldMapperCatalog build() {
            return new SubjectFieldMapperCatalog(mappers);
        }
    }
}
