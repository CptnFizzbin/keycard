package com.cptnfizzbin.keycard.action;

import lombok.AccessLevel;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.RequiredArgsConstructor;

import java.util.UUID;

@Getter
@EqualsAndHashCode
@RequiredArgsConstructor(access = AccessLevel.PRIVATE)
public final class Action {
    private final String name;

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

    public static Action create(String name) {
        return new Action(name, false);
    }

    /**
     * Generates a random id in place of a name and marks this Action dynamic - see {@link #isDynamic()}.
     */
    public static Action create() {
        return new Action(UUID.randomUUID().toString(), true);
    }

    public String getNameStr() {
        return name;
    }
}
