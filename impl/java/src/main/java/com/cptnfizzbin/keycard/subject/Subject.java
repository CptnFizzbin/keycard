package com.cptnfizzbin.keycard.subject;

import java.util.Optional;

/**
 * A named, type-safe subject - SPEC_V1-0.md §3.3's Subject position.
 * Unifies what used to be two separate types (a bare type token and a
 * wrapped instance reference) into one: {@link #getInstance()} is empty
 * for a bare type-check (§5, EC-7/EC-9 - no instance data for a
 * Conditions element to inspect) and present once {@link #wrap} is
 * called.
 */
public final class Subject<T> {
    private final String name;
    private final T instance;

    private Subject(String name, T instance) {
        this.name = name;
        this.instance = instance;
    }

    /** Creates a bare Subject for {@code name} - no wrapped instance until {@link #wrap} is called. */
    public static <T> Subject<T> create(String name) {
        return new Subject<>(name, null);
    }

    public String getName() {
        return name;
    }

    public Optional<T> getInstance() {
        return Optional.ofNullable(instance);
    }

    /** Returns a new Subject of the same name, wrapping {@code obj} as its instance. */
    public Subject<T> wrap(T obj) {
        return new Subject<>(name, obj);
    }
}
