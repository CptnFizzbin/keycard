package com.cptnfizzbin.keycard.subject;

import java.util.Optional;
import java.util.UUID;

/**
 * A named, type-safe subject - SPEC_V0.md §3.3's Subject position.
 * Unifies what used to be two separate types (a bare type token and a
 * wrapped instance reference) into one: {@link #getInstance()} is empty
 * for a bare type-check (§5, EC-7/EC-9 - no instance data for a
 * Conditions element to inspect) and present once {@link #wrap} is
 * called.
 */
public final class Subject<T> {
    private final String name;
    private final T instance;
    private final SubjectFieldMapper<T> fieldMapper;
    /**
     * Set only by {@link #create()}/{@link #create(SubjectFieldMapper)}
     * (the no-name overloads) - {@code name} then holds a randomly-
     * generated id rather than a developer-chosen name, and this Subject
     * MUST be registered as a catalog value on the {@code KeycardConfig}
     * handed to any {@code PolicyBuilder}/{@code Policy} that uses it, so
     * its catalog key can resolve to a real, stable, serializable name.
     * Carried through {@link #wrap} unchanged, same as {@code name}/
     * {@code fieldMapper}.
     */
    private final boolean dynamic;

    private Subject(String name, T instance, SubjectFieldMapper<T> fieldMapper, boolean dynamic) {
        this.name = name;
        this.instance = instance;
        this.fieldMapper = fieldMapper;
        this.dynamic = dynamic;
    }

    /** Creates a bare Subject for {@code name} - no wrapped instance until {@link #wrap} is called. */
    public static <T> Subject<T> create(String name) {
        return new Subject<>(name, null, null, false);
    }

    /** Like {@link #create(String)}, but with a {@link SubjectFieldMapper} carried through every {@link #wrap} unchanged (SPEC_V1-0-0.md §7.4.10/§7.4.11's field access). */
    public static <T> Subject<T> create(String name, SubjectFieldMapper<T> fieldMapper) {
        return new Subject<>(name, null, fieldMapper, false);
    }

    /** Generates a random id in place of a name and marks this Subject dynamic - see {@link #isDynamic()}. */
    public static <T> Subject<T> create() {
        return new Subject<>(UUID.randomUUID().toString(), null, null, true);
    }

    /** Like {@link #create()}, but with a {@link SubjectFieldMapper} carried through every {@link #wrap} unchanged. */
    public static <T> Subject<T> create(SubjectFieldMapper<T> fieldMapper) {
        return new Subject<>(UUID.randomUUID().toString(), null, fieldMapper, true);
    }

    public String getName() {
        return name;
    }

    public boolean isDynamic() {
        return dynamic;
    }

    public Optional<T> getInstance() {
        return Optional.ofNullable(instance);
    }

    public Optional<SubjectFieldMapper<T>> getFieldMapper() {
        return Optional.ofNullable(fieldMapper);
    }

    /** Returns a new Subject of the same name, wrapping {@code obj} as its instance. */
    public Subject<T> wrap(T obj) {
        return new Subject<>(name, obj, fieldMapper, dynamic);
    }
}
