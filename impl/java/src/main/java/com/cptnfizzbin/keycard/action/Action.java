package com.cptnfizzbin.keycard.action;

import java.util.UUID;

import lombok.EqualsAndHashCode;

public record Action(String id, @EqualsAndHashCode.Exclude boolean dynamic) {
    public Action() {
        this(UUID.randomUUID().toString(), true);
    }

    public Action(String name) {
        this(name, false);
    }

    public String getName() {
        return id;
    }
}
