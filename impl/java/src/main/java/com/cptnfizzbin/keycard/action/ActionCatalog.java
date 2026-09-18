package com.cptnfizzbin.keycard.action;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * A keyed catalog of Actions - each key becomes the serialized name for its
 * Action, which is how an {@link Action#create()} call with no name gets a
 * real, stable name. Handed to {@code KeycardConfig} via {@code
 * actionCatalog}; {@code KeycardConfig.Builder#addAction} is a shorthand
 * for building one entry-by-entry alongside the rest of the config.
 */
public final class ActionCatalog {
    private final Map<String, Action<?>> actions;

    private ActionCatalog(Map<String, Action<?>> actions) {
        this.actions = actions;
    }

    public static Builder builder() {
        return new Builder();
    }

    /** A snapshot of this catalog's entries. */
    public Map<String, Action<?>> toMap() {
        return Map.copyOf(actions);
    }

    /** A builder pre-populated with this catalog's existing entries - the basis for {@code KeycardConfig.Builder#addAction}. */
    public Builder toBuilder() {
        return new Builder(actions);
    }

    public static final class Builder {
        private final Map<String, Action<?>> actions;

        private Builder() {
            this.actions = new LinkedHashMap<>();
        }

        private Builder(Map<String, Action<?>> actions) {
            this.actions = new LinkedHashMap<>(actions);
        }

        public Builder add(String key, Action<?> action) {
            actions.put(key, action);
            return this;
        }

        public ActionCatalog build() {
            return new ActionCatalog(new LinkedHashMap<>(actions));
        }
    }
}
