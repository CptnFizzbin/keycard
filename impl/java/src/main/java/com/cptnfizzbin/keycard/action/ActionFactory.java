package com.cptnfizzbin.keycard.action;

public final class ActionFactory {
    private ActionFactory() {}

    public static <T extends String> Action<T> create(T name) {
        return Action.create(name);
    }

    /** Creates a dynamic Action - see {@link Action#create()}. */
    public static <T extends String> Action<T> create() {
        return Action.create();
    }
}
