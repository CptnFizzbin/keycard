package com.cptnfizzbin.keycard.action;

import lombok.Getter;
import lombok.experimental.Accessors;

import java.util.UUID;

@Getter
@Accessors(fluent = true)
public class Action {
    private final String id = UUID.randomUUID().toString();
    private final String name;
    private final Boolean dynamic;

    public Action() {
        this.name = this.id;
        this.dynamic = true;
    }

    public Action(String name) {
        this.name = name;
        this.dynamic = false;
    }
}
