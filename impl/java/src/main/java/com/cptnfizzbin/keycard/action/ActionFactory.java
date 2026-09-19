package com.cptnfizzbin.keycard.action;

public final class ActionFactory {
    private ActionFactory() {
    }

    public static Action create(String name) {
        return Action.create(name);
    }

    /**
     * Creates a dynamic Action - see {@link Action#create()}.
     */
    public static Action create() {
        return Action.create();
    }
}
