package com.cptnfizzbin.keycard.subject;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * A keyed catalog of Subjects - each key becomes the serialized name for
 * its Subject, which is how a {@link Subject#create()} call with no name
 * gets a real, stable name. Handed to {@code KeycardConfig} via {@code
 * subjectCatalog}; {@code KeycardConfig.Builder#addSubject} is a shorthand
 * for building one entry-by-entry alongside the rest of the config.
 */
public final class SubjectCatalog {
    private final Map<String, Subject<?>> subjects;

    private SubjectCatalog(Map<String, Subject<?>> subjects) {
        this.subjects = subjects;
    }

    public static Builder builder() {
        return new Builder();
    }

    /** A snapshot of this catalog's entries. */
    public Map<String, Subject<?>> toMap() {
        return Map.copyOf(subjects);
    }

    /** A builder pre-populated with this catalog's existing entries - the basis for {@code KeycardConfig.Builder#addSubject}. */
    public Builder toBuilder() {
        return new Builder(subjects);
    }

    public static final class Builder {
        private final Map<String, Subject<?>> subjects;

        private Builder() {
            this.subjects = new LinkedHashMap<>();
        }

        private Builder(Map<String, Subject<?>> subjects) {
            this.subjects = new LinkedHashMap<>(subjects);
        }

        public Builder add(String key, Subject<?> subject) {
            subjects.put(key, subject);
            return this;
        }

        public SubjectCatalog build() {
            return new SubjectCatalog(new LinkedHashMap<>(subjects));
        }
    }
}
