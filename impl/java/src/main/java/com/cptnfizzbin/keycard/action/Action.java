package com.cptnfizzbin.keycard.action;

import java.util.UUID;

import lombok.AccessLevel;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.RequiredArgsConstructor;

@Getter
@EqualsAndHashCode
@RequiredArgsConstructor(access = AccessLevel.PRIVATE)
public final class Action<T extends String> {
    private final T name;
    /**
     * Set only by {@link #create()} (no-arg) - {@code name} then holds a
     * randomly-generated id rather than a developer-chosen name, and this
     * Action MUST be registered as a catalog value on the {@code
     * KeycardConfig} handed to any {@code PolicyBuilder}/{@code Policy}
     * that uses it, so its catalog key can resolve to a real, stable,
     * serializable name. Excluded from equality/hashCode so those stay
     * name-based, unchanged from before this field existed.
     */
    @EqualsAndHashCode.Exclude
    private final boolean dynamic;

    public static <T extends String> Action<T> create(T name) {
        return new Action<>(name, false);
    }

    /** Generates a random id in place of a name and marks this Action dynamic - see {@link #isDynamic()}. */
    @SuppressWarnings("unchecked")
    public static <T extends String> Action<T> create() {
        return new Action<>((T) UUID.randomUUID().toString(), true);
    }

    public String getNameStr() {
        return name;
    }
}
