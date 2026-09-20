package com.cptnfizzbin.keycard.subject;


import lombok.Getter;
import lombok.experimental.Accessors;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Optional;
import java.util.UUID;

@Getter
@Accessors(fluent = true)
public final class Subject<T> {
    private final String id;
    private final String name;
    private final Boolean dynamic;

    @Nullable
    private final T claims;

    public Optional<T> claims() {
        return Optional.ofNullable(claims);
    }

    public Subject() {
        this.id = UUID.randomUUID().toString();
        this.name = this.id;
        this.dynamic = true;
        this.claims = null;
    }

    public Subject(String name) {
        this.id = UUID.randomUUID().toString();
        this.name = name;
        this.dynamic = false;
        this.claims = null;
    }

    private Subject(Subject<T> prev, @Nullable T instance) {
        this.id = prev.id;
        this.name = prev.name;
        this.dynamic = prev.dynamic;
        this.claims = instance;
    }

    public Subject<T> wrap(@NotNull T claims) {
        return new Subject<>(this, claims);
    }
}
