package com.cptnfizzbin.keycard;

import com.cptnfizzbin.keycard.action.Action;
import com.cptnfizzbin.keycard.builder.PolicyBuilder;
import com.cptnfizzbin.keycard.conditions.Condition;
import com.cptnfizzbin.keycard.conditions.ConditionResolver;
import com.cptnfizzbin.keycard.errors.PolicyArgumentException;
import com.cptnfizzbin.keycard.errors.PolicyLoadException;
import com.cptnfizzbin.keycard.policy.Policy;
import com.cptnfizzbin.keycard.policy.PolicyDefinition;
import com.cptnfizzbin.keycard.subject.Subject;
import org.junit.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.Assert.*;

/**
 * Regression coverage for bugs found in a review of the Java implementation.
 */
public class CodeReviewRegressionTest {
    public record Doc(long ownerId, Set<String> tags, String isbn) {}

    private static final Action READ = new Action("read");
    private static final Action DELETE = new Action("delete");
    private static final Subject<Doc, ?> DOC = new Subject<>("doc");

    // --- a built Policy is isolated from later mutation ---

    @Test
    public void builderCallsAfterBuildDoNotChangeAnAlreadyBuiltPolicy() {
        PolicyBuilder builder = new PolicyBuilder().allow(READ, DOC);
        Policy policy = builder.build();

        builder.allow(DELETE, DOC);

        assertFalse(policy.can(DELETE, DOC));
    }

    @Test
    public void mutatingTheDefinitionAfterLoadDoesNotChangeThePolicy() {
        PolicyDefinition def = new PolicyDefinition()
            .rules(new ArrayList<>(List.of(new PolicyDefinition.Rule("allow", "read", "doc"))));
        Policy policy = new Policy(def);

        def.rules().add(new PolicyDefinition.Rule("allow", "delete", "doc"));

        assertFalse(policy.can(DELETE, DOC));
    }

    // --- structural validation isn't gated by emitMeta ---

    @Test
    public void malformedEffectIsRejectedEvenWithEmitMetaOff() {
        PolicyDefinition def = new PolicyDefinition()
            .rules(List.of(new PolicyDefinition.Rule("Allow", "read", "doc")));

        assertThrows(PolicyLoadException.class, () -> new Policy(def, new KeycardConfig().emitMeta(false)));
    }

    @Test
    public void nullActionIsRejectedAtLoadEvenWithEmitMetaOff() {
        PolicyDefinition def = new PolicyDefinition()
            .rules(List.of(new PolicyDefinition.Rule("allow", null, "doc")));

        assertThrows(PolicyLoadException.class, () -> new Policy(def, new KeycardConfig().emitMeta(false)));
    }

    // --- $eq/$in/$has use value equality across boxed numeric types ---

    @Test
    public void integerConditionMatchesLongField() {
        ConditionResolver resolver = new ConditionResolver();
        Doc doc = new Doc(5, Set.of(), "x");

        assertTrue(resolver.evaluate(doc, Map.of("ownerId", 5)));
        assertTrue(resolver.evaluate(doc, Map.of("ownerId", Map.of("$in", List.of(4, 5)))));
        assertFalse(resolver.evaluate(doc, Map.of("ownerId", 6)));
    }

    @Test
    public void numericEqualityStaysExactForLargeLongs() {
        ConditionResolver resolver = new ConditionResolver();
        // 2^53 and 2^53 + 1 are the same double, but different longs.
        assertFalse(resolver.evaluate(9007199254740993L, 9007199254740992L));
        assertTrue(resolver.evaluate(5L, 5.0));
        assertFalse(resolver.evaluate(Double.NaN, Double.NaN));
    }

    @Test
    public void hasAndInAcceptAnyCollection() {
        Policy policy = new PolicyBuilder()
            .allow(READ, DOC, Condition.has(Doc::tags, "public"))
            .build();

        assertTrue(policy.can(READ, DOC.wrap(new Doc(1, Set.of("public"), "x"))));
        assertTrue(new ConditionResolver().evaluate(3, Map.of("$in", Set.of(1, 2, 3))));
    }

    // --- field names derived from method references ---

    @Test
    public void accessorStartingWithIsOrGetIsNotMangled() {
        assertEquals(Map.of("isbn", Map.of("$eq", "x")), Condition.eq(Doc::isbn, "x").toMap());
    }

    @Test
    public void lambdaGetterIsRejectedWithAClearError() {
        assertThrows(PolicyArgumentException.class, () -> Condition.eq((Doc d) -> d.isbn(), "x"));
    }
}
