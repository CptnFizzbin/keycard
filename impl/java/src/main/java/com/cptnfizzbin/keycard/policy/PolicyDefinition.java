package com.cptnfizzbin.keycard.policy;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.ToString;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/** The PolicyDefinition document shape - SPEC_V1-0.md §3. */
@Data
public final class PolicyDefinition {
    /** Required SemVer string, e.g. "1.0" - see SPEC_V1-0.md §2. */
    private final String version;
    /** Informational only - plays no role in evaluation. */
    private final String name;
    /** Informational only - plays no role in evaluation. */
    private final String description;
    private final Meta meta;
    private final List<Rule> rules;

    public PolicyDefinition(String version, List<Rule> rules) {
        this(version, null, null, null, rules);
    }

    // Hand-written: null-defaults and defensively copies rules, which a
    // generated @AllArgsConstructor wouldn't do.
    public PolicyDefinition(String version, String name, String description, Meta meta, List<Rule> rules) {
        this.version = version;
        this.name = name;
        this.description = description;
        this.meta = meta;
        this.rules = new ArrayList<>(rules != null ? rules : List.of());
    }

    // Hand-written: defensively copies, unlike a generated @Getter.
    public List<Rule> getRules() {
        return List.copyOf(rules);
    }

    /** `[Effect, Action, Subject, Conditions?]` - SPEC_V1-0.md §3.3. Ordered; declaration order is significant (§6). */
    @Data
    @AllArgsConstructor
    public static final class Rule {
        /** MUST be "allow" or "deny" - anything else is a malformed rule tuple (EC-10). */
        private final String effect;
        private final String action;
        private final String subjectName;
        /** Nullable - a rule with no conditions is unconditional. */
        private final Map<String, Object> conditions;
    }

    /**
     * SPEC_V1-0.md §3.2: the optional `meta` object, grouping six
     * independent, all-optional fields. Constructed via {@link #builder()}.
     * `anyAction`/`anySubject` are each tri-state - unset (a {@code null}
     * field here, meaning the "_ANY_" default applies), or a declared
     * {@link WildcardToken} ({@link WildcardToken.Disabled} or {@link
     * WildcardToken.Named}) - so a plain nullable {@code String} can't
     * represent them; see {@link WildcardToken} for the type-state this
     * replaces the previous boolean-pair workaround with.
     */
    @Getter
    @ToString
    @EqualsAndHashCode
    public static final class Meta {
        private final WildcardToken anyAction;
        private final WildcardToken anySubject;
        private final List<String> actions;
        private final List<String> subjects;
        private final List<String> operators;
        private final Object application;

        private Meta(
            WildcardToken anyAction, WildcardToken anySubject,
            List<String> actions, List<String> subjects, List<String> operators, Object application
        ) {
            this.anyAction = anyAction;
            this.anySubject = anySubject;
            this.actions = actions != null ? List.copyOf(actions) : null;
            this.subjects = subjects != null ? List.copyOf(subjects) : null;
            this.operators = operators != null ? List.copyOf(operators) : null;
            this.application = application;
        }

        public static Builder builder() {
            return new Builder();
        }

        public static final class Builder {
            private WildcardToken anyAction;
            private WildcardToken anySubject;
            private List<String> actions;
            private List<String> subjects;
            private List<String> operators;
            private Object application;

            /**
             * §3.2.1: declares the action wildcard token explicitly.
             * `value` is dispatched per {@link WildcardToken#of}: a
             * {@link String} names the token; {@code null}/{@code false}
             * disables the wildcard entirely; anything else throws
             * {@code PolicyLoadException} immediately.
             */
            public Builder anyAction(Object value) {
                return anyAction(WildcardToken.of(value));
            }

            /** Declares the action wildcard token from an already-resolved {@link WildcardToken} - e.g. one a caller (like {@code PolicyBuilder}) resolved earlier. */
            public Builder anyAction(WildcardToken token) {
                this.anyAction = token;
                return this;
            }

            /** §3.2.1: declares the subject wildcard token explicitly - symmetric with {@link #anyAction}. */
            public Builder anySubject(Object value) {
                return anySubject(WildcardToken.of(value));
            }

            /** Declares the subject wildcard token from an already-resolved {@link WildcardToken} - symmetric with {@link #anyAction(WildcardToken)}. */
            public Builder anySubject(WildcardToken token) {
                this.anySubject = token;
                return this;
            }

            public Builder actions(List<String> value) {
                this.actions = value;
                return this;
            }

            public Builder subjects(List<String> value) {
                this.subjects = value;
                return this;
            }

            public Builder operators(List<String> value) {
                this.operators = value;
                return this;
            }

            public Builder application(Object value) {
                this.application = value;
                return this;
            }

            public Meta build() {
                return new Meta(anyAction, anySubject, actions, subjects, operators, application);
            }
        }
    }
}
