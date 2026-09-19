package com.cptnfizzbin.keycard.subject;

import com.cptnfizzbin.keycard.action.Action;
import com.cptnfizzbin.keycard.action.ActionCatalog;

import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;

public final class SubjectCatalog extends HashMap<String, Subject<?>> {
    public SubjectCatalog add(String name, Subject<?> subject) {
        this.put(name, subject);
        return this;
    }
}

