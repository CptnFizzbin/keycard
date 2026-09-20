package com.cptnfizzbin.keycard.subject;


import lombok.Getter;
import lombok.experimental.Accessors;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Optional;
import java.util.UUID;

/**
 * {@code TSelf} is a self-bound (as with {@code Enum<E extends Enum<E>>}):
 * it lets {@link #wrap} return the real subtype with no cast, for a Subject
 * that's been extended into its own dedicated class (recommended - see
 * docs). Used directly - {@code new Subject<T, ?>()}, no subclass - it's
 * exactly today's simple/dynamic Subject, and {@link #copy} falls back to
 * rebuilding a plain {@code Subject} itself.
 * <p>
 * A dedicated subclass (e.g. {@code ArticleSubject extends
 * Subject<ArticleSubject.Claims, ArticleSubject>}) overrides {@link #copy}
 * to call its own private {@code (Subject, Claims)} constructor instead -
 * two lines, no cast anywhere, in the library or in application code -
 * so {@link #wrap} and any {@code from(...)} built on it return that exact
 * subtype.
 */
@Getter
@Accessors(fluent = true)
public class Subject<T, TSelf extends Subject<T, TSelf>> {
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

    protected Subject(Subject<T, TSelf> prev, @Nullable T instance) {
        this.id = prev.id;
        this.name = prev.name;
        this.dynamic = prev.dynamic;
        this.claims = instance;
    }

    public TSelf wrap(@NotNull T claims) {
        return copy(claims);
    }

    /**
     * Rebuilds this Subject with {@code instance} as its claims, preserving
     * id/name/dynamic. The base implementation covers plain, non-subclassed
     * use; a dedicated subclass overrides it - see the class doc.
     */
    @SuppressWarnings("unchecked")
    protected TSelf copy(@Nullable T instance) {
        return (TSelf) new Subject<>(this, instance);
    }
}
