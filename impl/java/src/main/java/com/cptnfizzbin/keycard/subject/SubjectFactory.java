package com.cptnfizzbin.keycard.subject;

public final class SubjectFactory {
    private SubjectFactory() {}

    public static <T> Subject<T> create(String name) {
        return Subject.create(name);
    }

    public static <T> Subject<T> create(String name, SubjectFieldMapper<T> fieldMapper) {
        return Subject.create(name, fieldMapper);
    }
}
