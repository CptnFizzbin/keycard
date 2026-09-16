package com.cptnfizzbin.keycard;

import lombok.AllArgsConstructor;
import lombok.Getter;
import org.junit.Test;

import com.cptnfizzbin.keycard.conditions.ConditionResolver;
import com.cptnfizzbin.keycard.conditions.Conditions;
import com.cptnfizzbin.keycard.conditions.Operator;

import java.util.Collections;
import java.util.List;
import java.util.Map;

import static org.junit.Assert.*;

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
        assertTrue(resolver.evaluate(5, Map.of("$eq", 5)));
        assertFalse(resolver.evaluate(5, Map.of("$eq", 3)));
    }

    @Test
    public void testGreaterThanCondition() {
        assertTrue(resolver.evaluate(10, Map.of("$gt", 5)));
        assertFalse(resolver.evaluate(3, Map.of("$gt", 5)));
    }

    @Test
    public void testInCondition() {
        assertTrue(resolver.evaluate(2, Map.of("$in", List.of(1, 2, 3))));
        assertFalse(resolver.evaluate(5, Map.of("$in", List.of(1, 2, 3))));
    }

    @Test
    public void testHasCondition() {
        assertTrue(resolver.evaluate(List.of(1, 2, 3), Map.of("$has", 2)));
        assertFalse(resolver.evaluate(List.of(1, 2, 3), Map.of("$has", 5)));
    }

    @Test
    public void testNotCondition() {
        assertTrue(resolver.evaluate("draft", Map.of("$not", Map.of("$eq", "published"))));
        assertFalse(resolver.evaluate("published", Map.of("$not", Map.of("$eq", "published"))));
    }

    @Test
    public void testFieldCondition() {
        Article article = new Article(1, 42, "published");
        assertTrue(resolver.evaluate(article, Conditions.eq(Article::getOwnerId, 42)));
        assertFalse(resolver.evaluate(article, Conditions.eq(Article::getOwnerId, 99)));
    }

    /** v1 supports only top-level field access (SPEC_V1-0.md §5.4.10): a second level of field narrowing always evaluates to false. */
    @Test
    public void testNestedFieldConditionIsRejected() {
        Map<String, Object> user = Map.of("name", "james");
        Map<String, Object> article = Map.of("id", 1, "owner", user);

        assertFalse(resolver.evaluate(article, Map.of("owner", Map.of("name", "james"))));
        assertFalse(resolver.evaluate(article, Map.of("owner", Map.of("name", "frank"))));
        assertFalse(resolver.evaluate(article, Map.of("owner", Map.of("name", Map.of("$ne", "frank")))));
    }

    /** A field condition's own value MAY still use logical/comparison operators against the narrowed value - only further field narrowing is disallowed. */
    @Test
    public void testFieldConditionWithOperatorValue() {
        Map<String, Object> user = Map.of("name", "james");
        Map<String, Object> article = Map.of("id", 1, "owner", user);

        assertTrue(resolver.evaluate(article, Map.of("owner", Collections.singletonMap("$ne", null))));
        assertFalse(resolver.evaluate(Collections.singletonMap("owner", null), Map.of("owner", Collections.singletonMap("$ne", null))));
    }

    /**
     * Issue 2: a custom operator receives an {@link com.cptnfizzbin.keycard.conditions.OperatorContext}
     * that lets it recurse into the condition language exactly like the
     * built-in $and/$or/$not do - here, a custom "$every" operator
     * (re-implementing $and via resolveSubcondition) over a fixed subject.
     */
    @Test
    public void customOperatorCanRecurseViaOperatorContext() {
        ConditionResolver withCustom = new ConditionResolver(List.of(
            Operator.of("$every", (subject, value, ctx) -> {
                for (Object condition : (List<?>) value) {
                    if (!ctx.resolveSubcondition(subject, condition)) return false;
                }
                return true;
            })
        ));

        Map<String, Object> article = Map.of("ownerId", 42, "status", "draft");

        assertTrue(withCustom.evaluate(article, Map.of("$every", List.of(
            Map.of("ownerId", 42),
            Map.of("status", "draft")
        ))));
        assertFalse(withCustom.evaluate(article, Map.of("$every", List.of(
            Map.of("ownerId", 42),
            Map.of("status", "published")
        ))));
    }
}
