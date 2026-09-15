package com.cptnfizzbin.keycard.spring;

import com.cptnfizzbin.keycard.subject.Subject;
import com.cptnfizzbin.keycard.subject.SubjectFactory;

import java.io.Serializable;
import java.util.function.Function;

/**
 * Names a domain object's Subject after its simple class name (e.g.
 * {@code Article} for {@code com.example.model.Article}) unless a
 * {@code targetType} is supplied directly. Good enough when Java class
 * names already match the Subject names used in your PolicyDefinitions;
 * pass a custom {@code nameResolver} (e.g. one that reads an annotation
 * or a registry) when they don't.
 */
public final class DefaultSubjectResolver implements KeycardSubjectResolver {
    private final Function<Object, String> nameResolver;

    public DefaultSubjectResolver() {
        this(obj -> obj.getClass().getSimpleName());
    }

    public DefaultSubjectResolver(Function<Object, String> nameResolver) {
        this.nameResolver = nameResolver;
    }

    @Override
    public Subject<Object> resolve(Object domainObject) {
        String name = nameResolver.apply(domainObject);
        return SubjectFactory.<Object>create(name).wrap(domainObject);
    }

    @Override
    public Subject<Object> resolve(Serializable targetId, String targetType) {
        // No instance was loaded for this check, so it's necessarily bare -
        // see the interface doc for why that rules out Conditions rules.
        return SubjectFactory.create(targetType);
    }
}
