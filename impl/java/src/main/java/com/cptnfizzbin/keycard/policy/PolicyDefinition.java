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
import java.util.List;
import java.util.Map;

/**
 * The PolicyDefinition document shape - SPEC_V0.md Jackson-annotated
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
     * Required SemVer string, e.g. "1.0.0" - see SPEC_V0.md
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
    private List<Rule> rules = new ArrayList<>();

    public List<Rule> getRules() {
        return List.copyOf(rules);
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

        public Meta anySubject(@Nullable Subject<?, ?> subject) {
            return subject != null
                ? anySubject(subject.name())
                : anySubject(false);
        }

        public Meta anySubject(String value) {
            this.anySubject = new WildcardToken.Named(value);
            return this;
        }

        public Meta anySubject(boolean enabled) {
            this.anySubject = enabled ? new WildcardToken.Named("_ANY_") : new WildcardToken.Disabled();
            return this;
        }

        public Meta anySubject(@Nullable WildcardToken token) {
            this.anySubject = token;
            return this;
        }

        public Meta anyAction(@Nullable Action action) {
            return action != null
                ? anyAction(action.name())
                : anyAction(false);
        }

        public Meta anyAction(String value) {
            this.anyAction = new WildcardToken.Named(value);
            return this;
        }

        public Meta anyAction(boolean enabled) {
            this.anyAction = enabled ? new WildcardToken.Named("_ANY_") : new WildcardToken.Disabled();
            return this;
        }

        public Meta anyAction(@Nullable WildcardToken token) {
            this.anyAction = token;
            return this;
        }
    }

    /**
     * `[Effect, Action, Subject, Conditions?]` - SPEC_V0.md Ordered; declaration order is significant.
     * {@code @JsonFormat(shape = ARRAY)} binds this straight from/to that tuple, positionally, rather than an
     * `{effect, action, ...}` object.
     */
    @Getter
    @Accessors(fluent = true)
    @JsonFormat(shape = JsonFormat.Shape.ARRAY)
    public static final class Rule {
        /**
         * MUST be "allow" or "deny" - anything else is a malformed rule tuple (EC-10).
         */
        @JsonProperty("effect")
        private final String effect;
        @JsonProperty("action")
        private final String action;
        @JsonProperty("subjectName")
        private final String subjectName;

        /**
         * Nullable - a rule with no conditions is unconditional.
         */
        @JsonProperty("conditions")
        private final Map<String, Object> conditions;

        public Rule(String effect, String action, String subjectName) {
            this.effect = effect;
            this.action = action;
            this.subjectName = subjectName;
            this.conditions = null;
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
            this.conditions = conditions;
        }
    }
}
