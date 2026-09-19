package com.cptnfizzbin.keycard;

import com.cptnfizzbin.keycard.action.Action;
import com.cptnfizzbin.keycard.action.ActionCatalog;
import com.cptnfizzbin.keycard.conditions.Operator;
import com.cptnfizzbin.keycard.lib.Logger;
import com.cptnfizzbin.keycard.subject.Subject;
import com.cptnfizzbin.keycard.subject.SubjectCatalog;
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
    /**
     * Symmetric with {@link #anyAction}.
     */
    private final Object anySubject;
    /**
     * Declared action vocabulary, additive to {@code meta.actions} (SPEC_V1-0-0.md §3.2.2, EC-8).
     */
    @Singular
    private final List<Action> actions;
    /**
     * Declared subject vocabulary, additive to {@code meta.subjects} (SPEC_V1-0-0.md §3.2.2, EC-8).
     */
    @Singular
    private final List<Subject<?>> subjects;
    /**
     * A keyed action catalog: each key becomes the serialized name for its
     * Action, which is how an {@link Action#create()} call with no name
     * gets a real, stable name. Build one directly via {@link
     * ActionCatalog#builder()}, or use {@link Builder#addAction} to add
     * entries one at a time alongside the rest of this config.
     */
    private final ActionCatalog actionCatalog;
    /**
     * Keyed subject catalog - see {@link #actionCatalog}.
     */
    private final SubjectCatalog subjectCatalog;
    /**
     * Custom operators to register alongside the built-ins (SPEC_V1-0-0.md §7.4.12).
     */
    @Singular
    private final List<Operator> operators;
    /**
     * SubjectFieldMappers registered by subject name - consulted when the Subject in hand doesn't carry its own field mapper. Null when never set.
     */
    private final SubjectFieldMapperCatalog mapper;
    /**
     * Logger for non-fatal diagnostics (currently: an unregistered dynamic Action/Subject encountered at {@code Policy}'s {@code can}/{@code cannot}/{@code require} time) - falls back to {@link Logger#NO_OP} when unset.
     */
    private final Logger logger;

    /**
     * Lombok fills in the rest of this builder (the plain setters, the
     * {@code @Singular} adders, and {@code build()}) around these two
     * hand-written convenience methods - see the Lombok {@code @Builder}
     * docs' "manually building" section.
     */
    public static class Builder {
        /**
         * Adds one entry to {@link #actionCatalog}, building it (or extending it) as needed - a shorthand for {@code ActionCatalog.builder().add(key, action).build()}.
         */
        public Builder addAction(String key, Action action) {
            this.actionCatalog = (this.actionCatalog != null ? this.actionCatalog.toBuilder() : ActionCatalog.builder())
                .add(key, action)
                .build();
            return this;
        }

        /**
         * Adds one entry to {@link #subjectCatalog} - see {@link #addAction}.
         */
        public Builder addSubject(String key, Subject<?> subject) {
            this.subjectCatalog = (this.subjectCatalog != null ? this.subjectCatalog.toBuilder() : SubjectCatalog.builder())
                .add(key, subject)
                .build();
            return this;
        }
    }
}
