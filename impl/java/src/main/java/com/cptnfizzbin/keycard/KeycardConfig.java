package com.cptnfizzbin.keycard;

import com.cptnfizzbin.keycard.action.Action;
import com.cptnfizzbin.keycard.conditions.Operator;
import com.cptnfizzbin.keycard.subject.Subject;
import com.cptnfizzbin.keycard.subject.SubjectFieldMapperCatalog;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

/**
 * Optional, shared config both {@link com.cptnfizzbin.keycard.policy.Policy}
 * and {@link com.cptnfizzbin.keycard.builder.PolicyBuilder} accept
 * alongside their existing constructors - one object bundling the actions/
 * subjects a policy is written against, its custom operators, and the
 * SubjectFieldMappers its subjects need, built once and handed to both
 * rather than kept in sync by hand. Every field is independently optional.
 */
public final class KeycardConfig {
    private final List<Action<?>> actions;
    private final List<Subject<?>> subjects;
    private final List<Operator> operators;
    private final SubjectFieldMapperCatalog mapper;

    private KeycardConfig(Builder builder) {
        this.actions = List.copyOf(builder.actions);
        this.subjects = List.copyOf(builder.subjects);
        this.operators = List.copyOf(builder.operators);
        this.mapper = builder.mapper;
    }

    public static Builder builder() {
        return new Builder();
    }

    /** Declared action vocabulary, additive to {@code meta.actions} (SPEC_V1-0-0.md §3.2.2, EC-8). */
    public List<Action<?>> getActions() {
        return actions;
    }

    /** Declared subject vocabulary, additive to {@code meta.subjects} (SPEC_V1-0-0.md §3.2.2, EC-8). */
    public List<Subject<?>> getSubjects() {
        return subjects;
    }

    /** Custom operators to register alongside the built-ins (SPEC_V1-0-0.md §7.4.12). */
    public List<Operator> getOperators() {
        return operators;
    }

    /** SubjectFieldMappers registered by subject name - consulted when the Subject in hand doesn't carry its own field mapper. Null when never set. */
    public SubjectFieldMapperCatalog getMapper() {
        return mapper;
    }

    public static final class Builder {
        private final List<Action<?>> actions = new ArrayList<>();
        private final List<Subject<?>> subjects = new ArrayList<>();
        private final List<Operator> operators = new ArrayList<>();
        private SubjectFieldMapperCatalog mapper;

        private Builder() {}

        public Builder actions(Collection<Action<?>> actions) {
            this.actions.addAll(actions);
            return this;
        }

        public Builder subjects(Collection<Subject<?>> subjects) {
            this.subjects.addAll(subjects);
            return this;
        }

        public Builder operators(Collection<Operator> operators) {
            this.operators.addAll(operators);
            return this;
        }

        public Builder mapper(SubjectFieldMapperCatalog mapper) {
            this.mapper = mapper;
            return this;
        }

        public KeycardConfig build() {
            return new KeycardConfig(this);
        }
    }
}
