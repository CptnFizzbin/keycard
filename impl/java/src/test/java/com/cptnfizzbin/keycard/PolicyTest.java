package com.cptnfizzbin.keycard;

import com.cptnfizzbin.keycard.action.Action;
import com.cptnfizzbin.keycard.action.ActionFactory;
import com.cptnfizzbin.keycard.builder.PolicyBuilder;
import com.cptnfizzbin.keycard.conditions.Condition;
import com.cptnfizzbin.keycard.conditions.Operator;
import com.cptnfizzbin.keycard.errors.PolicyException;
import com.cptnfizzbin.keycard.policy.Policy;
import com.cptnfizzbin.keycard.subject.Subject;
import com.cptnfizzbin.keycard.subject.SubjectFactory;
import lombok.AllArgsConstructor;
import lombok.Getter;
import org.junit.Test;

import java.util.List;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class PolicyTest {
    @Getter
    @AllArgsConstructor
    static class Article {
        private final int id;
        private final int ownerId;
        private final String status;
    }

    @Test
    public void testCanCheckByDefinition() {
        Subject<Article> article = SubjectFactory.create("Article");
        Action create = ActionFactory.create("Create");

        Policy policy = new PolicyBuilder()
            .allow(create, article)
            .build();

        assertTrue(policy.can(create, article));
    }

    @Test
    public void testCanCheckByReference() {
        Subject<Article> article = SubjectFactory.create("Article");
        Action update = ActionFactory.create("Update");

        Article data = new Article(1, 42, "published");
        Policy policy = new PolicyBuilder()
            .allow(update, article, Condition.eq(Article::getOwnerId, 42))
            .build();

        assertTrue(policy.can(update, article.wrap(data)));
    }

    @Test
    public void testCannotCheck() {
        Subject<Article> article = SubjectFactory.create("Article");
        Action delete = ActionFactory.create("Delete");

        Policy policy = new PolicyBuilder()
            .deny(delete, article)
            .build();

        assertTrue(policy.cannot(delete, article));
    }

    @Test
    public void testRequireAllowed() {
        Subject<Article> article = SubjectFactory.create("Article");
        Action create = ActionFactory.create("Create");

        Policy policy = new PolicyBuilder()
            .allow(create, article)
            .build();

        // Should not throw
        policy.require(create, article);
    }

    @Test(expected = PolicyException.class)
    public void testRequireDenied() {
        Subject<Article> article = SubjectFactory.create("Article");
        Action delete = ActionFactory.create("Delete");

        Policy policy = new PolicyBuilder()
            .deny(delete, article)
            .build();

        policy.require(delete, article);
    }

    @Test
    public void testDenyOverridesAllow() {
        Subject<Article> article = SubjectFactory.create("Article");
        Action delete = ActionFactory.create("Delete");

        Policy policy = new PolicyBuilder()
            .allow(delete, article)
            .deny(delete, article)
            .build();

        assertFalse(policy.can(delete, article));
        assertTrue(policy.cannot(delete, article));
    }

    @Test
    public void testDenyWithConditionOnlyOverridesWhenItMatches() {
        Subject<Article> article = SubjectFactory.create("Article");
        Action delete = ActionFactory.create("Delete");

        Policy policy = new PolicyBuilder()
            .allow(delete, article)
            .deny(delete, article, Condition.field(Article::getStatus, "archived"))
            .build();

        assertFalse(policy.can(delete, article.wrap(new Article(1, 1, "archived"))));
        assertTrue(policy.can(delete, article.wrap(new Article(1, 1, "published"))));
    }

    @Test
    public void testLastRuleWinsReopensWhatAnEarlierDenyClosed() {
        Subject<Article> article = SubjectFactory.create("Article");
        Action delete = ActionFactory.create("Delete");

        Policy policy = new PolicyBuilder()
            .deny(delete, article)
            .allow(delete, article)
            .build();

        assertTrue(policy.can(delete, article));
    }

    /**
     * `allow`/`deny`/`can`/`cannot`/`require` always take a real
     * `Action` and `Subject<?>` - no bare-`String` or raw-instance
     * overloads. A bare Subject (no `.wrap()`) is a type-only check; a
     * wrapped one carries instance data a Conditions element can inspect.
     */
    @Test
    public void alwaysRequiresActionAndSubject() {
        Subject<Article> article = SubjectFactory.create("Article");
        Action read = ActionFactory.create("Read");
        Action update = ActionFactory.create("Update");

        Policy policy = new PolicyBuilder()
            .allow(read, article)
            .allow(update, article, Condition.eq(Article::getOwnerId, 42))
            .build();

        assertTrue(policy.can(read, article));
        assertFalse(policy.cannot(read, article));
        policy.require(read, article); // should not throw

        Article owned = new Article(1, 42, "published");
        assertTrue(policy.can(update, article.wrap(owned)));

        Article notOwned = new Article(1, 1, "published");
        assertFalse(policy.can(update, article.wrap(notOwned)));
    }

    /**
     * Issue 1: a custom operator supplied to {@code PolicyBuilder} carries
     * through {@code build()} into the constructed {@code Policy} - a
     * builder-produced definition doesn't need its operators re-supplied
     * separately at {@code Policy.from(...)}.
     */
    @Test
    public void builderSuppliedOperatorsCarryThroughToTheBuiltPolicy() {
        Subject<Article> article = SubjectFactory.create("Article");
        Action delete = ActionFactory.create("Delete");

        Policy policy = new PolicyBuilder(List.of(
            Operator.of("$hasRole", (subject, value, ctx) -> "admin".equals(value))
        ))
            .deny(delete, article)
            .allow(delete, article, Condition.op("$hasRole", "admin"))
            .build();

        assertTrue(policy.can(delete, article.wrap(new Article(1, 1, "published"))));
    }
}
