package com.cptnfizzbin.keycard;

import com.cptnfizzbin.keycard.action.Action;
import com.cptnfizzbin.keycard.conditions.Operator;
import com.cptnfizzbin.keycard.subject.Subject;
import com.cptnfizzbin.keycard.subject.SubjectFieldMapperCatalog;

import lombok.Builder;
import lombok.Getter;
import lombok.Singular;

import java.util.List;

/**
 * Optional, shared config both {@link com.cptnfizzbin.keycard.policy.Policy}
 * and {@link com.cptnfizzbin.keycard.builder.PolicyBuilder} accept alongside
 * their existing constructors - one object bundling the wildcard tokens
 * (SPEC_V1-0-0.md §3.2.1), the actions/subjects a policy is written
 * against, its custom operators, and the SubjectFieldMappers its subjects
 * need, built once and handed to both rather than kept in sync by hand.
 * Every field is independently optional - an unset {@code @Singular} list
 * comes back empty, never null.
 */
@Getter
@Builder(builderClassName = "Builder")
public final class KeycardConfig {
    /**
     * SPEC_V1-0-0.md §3.2.1's action wildcard token, consumed by {@code
     * PolicyBuilder}'s {@code KeycardConfig} constructor via {@code
     * WildcardToken.of}: a {@link String} names the token; {@link
     * Boolean#FALSE} disables it entirely; unset ({@code null}, this
     * field's default) leaves it "not declared", so the "_ANY_" default
     * applies. Unlike {@code PolicyBuilder}'s {@code (Object, Object)}
     * constructors, a {@code null} passed here is indistinguishable from
     * never setting it at all - {@link Boolean#FALSE} is how this path
     * disables a wildcard explicitly.
     */
    private final Object anyAction;
    /** Symmetric with {@link #anyAction}. */
    private final Object anySubject;
    /** Declared action vocabulary, additive to {@code meta.actions} (SPEC_V1-0-0.md §3.2.2, EC-8). */
    @Singular
    private final List<Action<?>> actions;
    /** Declared subject vocabulary, additive to {@code meta.subjects} (SPEC_V1-0-0.md §3.2.2, EC-8). */
    @Singular
    private final List<Subject<?>> subjects;
    /** Custom operators to register alongside the built-ins (SPEC_V1-0-0.md §7.4.12). */
    @Singular
    private final List<Operator> operators;
    /** SubjectFieldMappers registered by subject name - consulted when the Subject in hand doesn't carry its own field mapper. Null when never set. */
    private final SubjectFieldMapperCatalog mapper;
}
