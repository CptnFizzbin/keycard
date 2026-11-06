package com.cptnfizzbin.keycard;

import com.cptnfizzbin.keycard.conditions.Condition;
import com.cptnfizzbin.keycard.conditions.ConditionResolver;
import com.cptnfizzbin.keycard.conditions.Operator;
import com.cptnfizzbin.keycard.conditions.OperatorCatalog;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.val;
import org.junit.Test;

import java.util.Collections;
import java.util.List;
import java.util.Map;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class ConditionResolverTest {
    private final ConditionResolver resolver = new ConditionResolver();

    @Getter
    @AllArgsConstructor
    static class Article {
        private final int id;
        private final int ownerId;
        private final String status;
    }

    @Test
    public void testEqualityCondition() {
        assertTrue(resolver.evaluate(5, Condition.op("$eq", 5)));
        assertFalse(resolver.evaluate(5, Condition.op("$eq", 3)));
    }

    @Test
    public void testGreaterThanCondition() {
        assertTrue(resolver.evaluate(10, Condition.op("$gt", 5)));
        assertFalse(resolver.evaluate(3, Condition.op("$gt", 5)));
    }

    @Test
    public void testInCondition() {
        assertTrue(resolver.evaluate(2, Condition.op("$in", List.of(1, 2, 3))));
        assertFalse(resolver.evaluate(5, Condition.op("$in", List.of(1, 2, 3))));
    }

    @Test
    public void testHasCondition() {
        assertTrue(resolver.evaluate(List.of(1, 2, 3), Condition.op("$has", 2)));
        assertFalse(resolver.evaluate(List.of(1, 2, 3), Condition.op("$has", 5)));
    }

    @Test
    public void testNotCondition() {
        assertTrue(resolver.evaluate("draft", Condition.not(Condition.op("$eq", "published"))));
        assertFalse(resolver.evaluate("published", Condition.not(Condition.op("$eq", "published"))));
    }

    @Test
    public void testAlwaysCondition() {
        assertTrue(resolver.evaluate("draft", Condition.always()));
        assertTrue(resolver.evaluate(null, Condition.always()));
        assertFalse(resolver.evaluate("draft", Condition.not(Condition.always())));
    }

    @Test
    public void testNeverCondition() {
        assertFalse(resolver.evaluate("draft", Condition.never()));
        assertFalse(resolver.evaluate(null, Condition.never()));
        assertTrue(resolver.evaluate("draft", Condition.not(Condition.never())));
    }

    @Test
    public void testConstantConditionsRejectNonTrueOperands() {
        for (Object operand : new Object[] { false, "true", 1, null }) {
            assertFalse(resolver.evaluate("draft", Condition.op("$always", operand)));
            assertFalse(resolver.evaluate("draft", Condition.op("$never", operand)));
        }
    }

    @Test
    public void testFieldCondition() {
        Article article = new Article(1, 42, "published");
        assertTrue(resolver.evaluate(article, Condition.eq(Article::getOwnerId, 42)));
        assertFalse(resolver.evaluate(article, Condition.eq(Article::getOwnerId, 99)));
    }

    /**
     * The spec supports only top-level field access: a second level of field narrowing always evaluates to false.
     */
    @Test
    public void testNestedFieldConditionIsRejected() {
        Map<String, Object> user = Map.of("name", "james");
        Map<String, Object> article = Map.of("id", 1, "owner", user);

        assertFalse(resolver.evaluate(article, Map.of("owner", Map.of("name", "james"))));
        assertFalse(resolver.evaluate(article, Map.of("owner", Map.of("name", "frank"))));
        assertFalse(resolver.evaluate(article, Map.of("owner", Map.of("name", Map.of("$ne", "frank")))));
    }

    /**
     * A field condition's own value MAY still use logical/comparison operators against the narrowed value - only further field narrowing is disallowed.
     */
    @Test
    public void testFieldConditionWithOperatorValue() {
        Map<String, Object> user = Map.of("name", "james");
        Map<String, Object> article = Map.of("id", 1, "owner", user);

        assertTrue(resolver.evaluate(article, Map.of("owner", Condition.op("$ne", null))));
        assertFalse(resolver.evaluate(Collections.singletonMap("owner", null), Map.of("owner", Condition.op("$ne", null))));
    }

    @Test
    public void customOperatorCanRecurseViaOperatorContext() {
        val operators = new OperatorCatalog()
            .add(Operator.of("$every", (subject, value, ctx) -> {
                for (Object condition : (List<?>) value) {
                    if (!ctx.resolveSubcondition(subject, condition)) return false;
                }
                return true;
            }));

        ConditionResolver withCustom = new ConditionResolver(operators);

        Map<String, Object> article = Map.of("ownerId", 42, "status", "draft");

        assertTrue(withCustom.evaluate(article, Condition.op("$every", List.of(
            Map.of("ownerId", 42),
            Map.of("status", "draft")
        ))));
        assertFalse(withCustom.evaluate(article, Condition.op("$every", List.of(
            Map.of("ownerId", 42),
            Map.of("status", "published")
        ))));
    }
}
