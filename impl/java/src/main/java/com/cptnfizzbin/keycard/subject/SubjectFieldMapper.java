package com.cptnfizzbin.keycard.subject;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.function.Function;

/**
 * Per-field getters for a Subject's wrapped instance - the explicit
 * counterpart to {@code FieldAccess}'s reflection-based lookup
 * (SPEC_V1-0-0.md §7.4.10/§7.4.11), which reads a declared field directly.
 * Lets a condition reference a field whose name doesn't match the
 * instance's own field names (a rename, a computed/derived value), or an
 * instance whose fields reflection can't reach (getters only, no visible
 * fields). Consulted per-field: a field this mapper doesn't define still
 * falls back to reflection.
 */
public final class SubjectFieldMapper<T> {
    private final Map<String, Function<T, Object>> getters;

    private SubjectFieldMapper(Map<String, Function<T, Object>> getters) {
        this.getters = new LinkedHashMap<>(getters);
    }

    public static <T> Builder<T> builder() {
        return new Builder<>();
    }

    public boolean hasField(String fieldName) {
        return getters.containsKey(fieldName);
    }

    /** Applies the getter registered for {@code fieldName} to {@code instance}. Callers MUST check {@link #hasField} first. */
    @SuppressWarnings("unchecked")
    public Object get(Object instance, String fieldName) {
        return getters.get(fieldName).apply((T) instance);
    }

    public static final class Builder<T> {
        private final Map<String, Function<T, Object>> getters = new LinkedHashMap<>();

        private Builder() {}

        public Builder<T> field(String name, Function<T, Object> getter) {
            getters.put(name, getter);
            return this;
        }

        public SubjectFieldMapper<T> build() {
            return new SubjectFieldMapper<>(getters);
        }
    }
}
