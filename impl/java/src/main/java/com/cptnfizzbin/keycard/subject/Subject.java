package com.cptnfizzbin.keycard.subject;


import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Optional;
import java.util.UUID;

public final class Subject<T> {
    public final String id;

    @Nullable
    private final T instance;

    @Nullable
    private final SubjectFieldMapper<T> fieldMapper;

    private final boolean dynamic;

    public Subject() {
        this((SubjectFieldMapper<T>) null);
    }

    public Subject(String name) {
        this(name, null);
    }

    public Subject(String name, @Nullable SubjectFieldMapper<T> fieldMapper) {
        this.dynamic = false;
        this.id = name;
        this.fieldMapper = fieldMapper;
        this.instance = null;
    }

    public Subject(@Nullable SubjectFieldMapper<T> fieldMapper) {
        this.dynamic = true;
        this.id = UUID.randomUUID().toString();
        this.fieldMapper = fieldMapper;
        this.instance = null;
    }

    private Subject(Subject<T> subject, @NotNull T instance) {
        this.id = subject.id;
        this.dynamic = subject.dynamic;
        this.fieldMapper = subject.fieldMapper;
        this.instance = instance;
    }

    public Optional<T> getInstance() {
        return Optional.ofNullable(instance);
    }

    public Optional<SubjectFieldMapper<T>> getFieldMapper() {
        return Optional.ofNullable(fieldMapper);
    }

    /**
     * Returns a new Subject of the same name, wrapping {@code obj} as its instance.
     */
    public Subject<T> wrap(@NotNull T obj) {
        return new Subject<>(this, obj);
    }
}
