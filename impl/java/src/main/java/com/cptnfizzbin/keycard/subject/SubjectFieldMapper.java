package com.cptnfizzbin.keycard.subject;

import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;

/**
 * Per-field getters for a Subject's wrapped instance - the explicit
 * counterpart to {@code FieldAccess}'s reflection-based lookup
 *, which reads a declared field directly.
 * Lets a condition reference a field whose name doesn't match the
 * instance's own field names (a rename, a computed/derived value), or an
 * instance whose fields reflection can't reach (getters only, no visible
 * fields). Consulted per-field: a field this mapper doesn't define still
 * falls back to reflection.
 */
public final class SubjectFieldMapper<T> extends HashMap<String, Function<T, Object>> {
    public SubjectFieldMapper<T> map(String field, Function<T, Object> getter) {
        this.put(field, getter);
        return this;
    }

    public Optional<Object> getValue(String field, T obj) {
        return Optional.ofNullable(this.get(field))
            .map(getter -> getter.apply(obj));
    }
}
