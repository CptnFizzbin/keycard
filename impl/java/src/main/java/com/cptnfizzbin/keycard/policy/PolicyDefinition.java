package com.cptnfizzbin.keycard.policy;

import com.cptnfizzbin.keycard.version.KeyCardVersion;
import lombok.*;
import lombok.experimental.Accessors;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * The PolicyDefinition document shape - SPEC_V0.md §3.
 */
@Data
@Accessors(fluent = true, chain = true)
@NoArgsConstructor
public final class PolicyDefinition {
    /**
     * Required SemVer string, e.g. "1.0.0" - see SPEC_V0.md §2.
     */
    private String version = KeyCardVersion.KEYCARD_POLICY_VERSION.toString();
    /**
     * Informational only - plays no role in evaluation.
     */
    private String name = null;
    /**
     * Informational only - plays no role in evaluation.
     */
    private String description = null;
    private Meta meta = null;
    private List<Rule> rules = new ArrayList<>();

    public List<Rule> getRules() {
        return List.copyOf(rules);
    }

    @Data
    @Accessors(fluent = true, chain = true)
    @NoArgsConstructor
    @ToString
    @EqualsAndHashCode
    public static final class Meta {
        private WildcardToken anyAction = null;
        private WildcardToken anySubject = null;
        private List<String> actions = null;
        private List<String> subjects = null;
        private List<String> operators = null;
        private Object application = null;

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
     * `[Effect, Action, Subject, Conditions?]` - SPEC_V0.md §3.3. Ordered; declaration order is significant (§6).
     */
    @Getter
    @Accessors(fluent = true)
    public static final class Rule {
        /**
         * MUST be "allow" or "deny" - anything else is a malformed rule tuple (EC-10).
         */
        private final String effect;
        private final String action;
        private final String subjectName;

        /**
         * Nullable - a rule with no conditions is unconditional.
         */
        private final Map<String, Object> conditions;

        public Rule(String effect, String action, String subjectName) {
            this.effect = effect;
            this.action = action;
            this.subjectName = subjectName;
            this.conditions = null;
        }

        public Rule(String effect, String action, String subjectName, Map<String, Object> conditions) {
            this.effect = effect;
            this.action = action;
            this.subjectName = subjectName;
            this.conditions = conditions;
        }
    }
}
