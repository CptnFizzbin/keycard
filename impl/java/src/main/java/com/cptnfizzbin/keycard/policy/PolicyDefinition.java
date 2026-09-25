package com.cptnfizzbin.keycard.policy;

import com.cptnfizzbin.keycard.action.Action;
import com.cptnfizzbin.keycard.subject.Subject;
import com.cptnfizzbin.keycard.version.KeyCardVersion;
import com.fasterxml.jackson.annotation.*;
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import lombok.*;
import lombok.experimental.Accessors;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * The PolicyDefinition document shape, Jackson-annotated
 * so any consumer with a Jackson (de)serializer for their format of
 * choice - YAML, JSON, ... - can bind a document straight to/from this
 * type. This only pulls in jackson-databind (for the annotation types
 * and the custom {@link WildcardToken} (de)serializers below), not a
 * format module, so KeyCard itself still never reads or writes
 * policy.yaml text - resolving a document's actual bytes is left to
 * whatever format module a consumer picks.
 */
@Data
@Accessors(fluent = true, chain = true)
@NoArgsConstructor
@JsonIgnoreProperties(ignoreUnknown = true)
@JsonInclude(JsonInclude.Include.NON_NULL)
public final class PolicyDefinition {
    /**
     * Required SemVer string, e.g. "1.0.0".
     */
    @JsonProperty("version")
    private String version = KeyCardVersion.KEYCARD_POLICY_VERSION.toString();
    /**
     * Informational only - plays no role in evaluation.
     */
    @JsonProperty("name")
    private String name = null;
    /**
     * Informational only - plays no role in evaluation.
     */
    @JsonProperty("description")
    private String description = null;
    @JsonProperty("meta")
    private Meta meta = null;
    @JsonProperty("rules")
    @Getter(AccessLevel.NONE)
    @Setter(AccessLevel.NONE)
    private List<Rule> rules = new ArrayList<>();

    /** An unmodifiable snapshot of the rules, in declaration order. */
    public List<Rule> rules() {
        // null when a bound document says "rules: null"
        return rules != null ? List.copyOf(rules) : List.of();
    }

    /** Replaces the rules with a copy of {@code rules}, so later changes to the passed list don't leak in. */
    public PolicyDefinition rules(List<Rule> rules) {
        this.rules = new ArrayList<>(rules);
        return this;
    }

    @Data
    @Accessors(fluent = true, chain = true)
    @NoArgsConstructor
    @ToString
    @EqualsAndHashCode
    @JsonIgnoreProperties(ignoreUnknown = true)
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public static final class Meta {
        @JsonProperty("anyAction")
        @JsonDeserialize(using = WildcardTokenDeserializer.class)
        @JsonSerialize(using = WildcardTokenSerializer.class)
        private WildcardToken anyAction = null;

        @JsonProperty("anySubject")
        @JsonDeserialize(using = WildcardTokenDeserializer.class)
        @JsonSerialize(using = WildcardTokenSerializer.class)
        private WildcardToken anySubject = null;

        @JsonProperty("actions")
        private List<String> actions = null;

        @JsonProperty("subjects")
        private List<String> subjects = null;

        @JsonProperty("operators")
        private List<String> operators = null;

        @JsonProperty("application")
        private Object application = null;

        /** Declares {@code subject}'s name as the subject wildcard token. */
        public Meta anySubject(Subject<?, ?> subject) {
            return anySubject(Objects.requireNonNull(subject, "subject - use disableAnySubject() to disable the wildcard").name());
        }

        /** Declares {@code value} as the subject wildcard token. */
        public Meta anySubject(String value) {
            this.anySubject = new WildcardToken.Named(Objects.requireNonNull(value, "value - use disableAnySubject() to disable the wildcard"));
            return this;
        }

        /** Disables the subject wildcard. Same as {@code anySubject(false)}. */
        public Meta disableAnySubject() {
            return anySubject(false);
        }

        public Meta anySubject(boolean enabled) {
            this.anySubject = enabled ? new WildcardToken.Named("_ANY_") : new WildcardToken.Disabled();
            return this;
        }

        /** Sets the raw token - {@code null} means "not declared" ({@code "_ANY_"} applies). */
        public Meta anySubject(@Nullable WildcardToken token) {
            this.anySubject = token;
            return this;
        }

        /** Declares {@code action}'s name as the action wildcard token. */
        public Meta anyAction(Action action) {
            return anyAction(Objects.requireNonNull(action, "action - use disableAnyAction() to disable the wildcard").name());
        }

        /** Declares {@code value} as the action wildcard token. */
        public Meta anyAction(String value) {
            this.anyAction = new WildcardToken.Named(Objects.requireNonNull(value, "value - use disableAnyAction() to disable the wildcard"));
            return this;
        }

        /** Disables the action wildcard. Same as {@code anyAction(false)}. */
        public Meta disableAnyAction() {
            return anyAction(false);
        }

        public Meta anyAction(boolean enabled) {
            this.anyAction = enabled ? new WildcardToken.Named("_ANY_") : new WildcardToken.Disabled();
            return this;
        }

        /** Sets the raw token - {@code null} means "not declared" ({@code "_ANY_"} applies). */
        public Meta anyAction(@Nullable WildcardToken token) {
            this.anyAction = token;
            return this;
        }
    }

    /**
     * `[Effect, Action, Subject, Conditions?]`. Ordered; declaration order is significant.
     * {@code @JsonFormat(shape = ARRAY)} binds this straight from/to that tuple, positionally, rather than an
     * `{effect, action, ...}` object.
     */
    @Getter
    @Accessors(fluent = true)
    @EqualsAndHashCode
    @ToString
    @JsonFormat(shape = JsonFormat.Shape.ARRAY)
    public static final class Rule {
        /**
         * MUST be "allow" or "deny" - anything else is a malformed rule tuple.
         */
        @JsonProperty("effect")
        private final String effect;
        @JsonProperty("action")
        private final String action;
        @JsonProperty("subjectName")
        private final String subjectName;

        /**
         * Nullable - a rule with no conditions is unconditional. A deep,
         * unmodifiable copy of what was passed in, so a Rule can't be changed
         * after a Policy has validated it.
         */
        @JsonProperty("conditions")
        private final Map<String, Object> conditions;

        public Rule(String effect, String action, String subjectName) {
            this(effect, action, subjectName, null);
        }

        @JsonCreator
        public Rule(
            @JsonProperty("effect") String effect,
            @JsonProperty("action") String action,
            @JsonProperty("subjectName") String subjectName,
            @JsonProperty("conditions") Map<String, Object> conditions
        ) {
            this.effect = effect;
            this.action = action;
            this.subjectName = subjectName;
            this.conditions = conditions != null ? freezeMap(conditions) : null;
        }

        /** Recursively copies a Conditions tree into unmodifiable maps/lists - {@code null} values are kept (an explicit {@code $eq: null}). */
        private static Map<String, Object> freezeMap(Map<?, ?> map) {
            Map<String, Object> copy = new LinkedHashMap<>();
            map.forEach((k, v) -> copy.put(String.valueOf(k), freeze(v)));
            return Collections.unmodifiableMap(copy);
        }

        private static Object freeze(Object value) {
            if (value instanceof Map<?, ?> map) return freezeMap(map);
            if (value instanceof Collection<?> collection) {
                List<Object> copy = new ArrayList<>(collection.size());
                collection.forEach(v -> copy.add(freeze(v)));
                return Collections.unmodifiableList(copy);
            }
            return value;
        }
    }
}
