package com.cptnfizzbin.keycard;

import com.cptnfizzbin.keycard.action.Action;
import com.cptnfizzbin.keycard.action.ActionFactory;
import com.cptnfizzbin.keycard.builder.PolicyBuilder;
import com.cptnfizzbin.keycard.conditions.Condition;
import com.cptnfizzbin.keycard.conditions.Operator;
import com.cptnfizzbin.keycard.policy.Policy;
import com.cptnfizzbin.keycard.subject.Subject;
import com.cptnfizzbin.keycard.subject.SubjectFactory;
import lombok.AllArgsConstructor;
import lombok.Getter;
import org.junit.Test;

import java.util.List;
import java.util.Map;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class ConditionHelperTest {
    @Getter
    @AllArgsConstructor
    static class Article {
        private final int id;
        private final int ownerId;
        private final String status;
        private final int viewCount;
        private final List<String> tags;
    }

    @Test
    public void testFieldEquality() {
        Subject<Article> article = SubjectFactory.create("Article");
        Action update = ActionFactory.create("Update");

        Policy policy = new PolicyBuilder()
            .allow(update, article, Condition.eq(Article::getOwnerId, 42))
            .build();

        Article data = new Article(1, 42, "published", 100, List.of());
        assertTrue(policy.can(update, article.wrap(data)));

        Article other = new Article(1, 99, "published", 100, List.of());
        assertFalse(policy.can(update, article.wrap(other)));
    }

    @Test
    public void testFieldNotEqual() {
        Subject<Article> article = SubjectFactory.create("Article");
        Action update = ActionFactory.create("Update");

        Policy policy = new PolicyBuilder()
            .allow(update, article, Condition.ne(Article::getStatus, "archived"))
            .build();

        Article published = new Article(1, 1, "published", 100, List.of());
        assertTrue(policy.can(update, article.wrap(published)));

        Article archived = new Article(1, 1, "archived", 100, List.of());
        assertFalse(policy.can(update, article.wrap(archived)));
    }

    @Test
    public void testNumberComparison() {
        Subject<Article> article = SubjectFactory.create("Article");
        Action delete = ActionFactory.create("Delete");

        Policy policy = new PolicyBuilder()
            .allow(delete, article, Condition.lt(Article::getViewCount, 1000))
            .build();

        Article lowViews = new Article(1, 1, "published", 500, List.of());
        assertTrue(policy.can(delete, article.wrap(lowViews)));

        Article highViews = new Article(1, 1, "published", 5000, List.of());
        assertFalse(policy.can(delete, article.wrap(highViews)));
    }

    @Test
    public void testAndCondition() {
        Subject<Article> article = SubjectFactory.create("Article");
        Action update = ActionFactory.create("Update");

        Map<String, Object> conditions = Condition.and(
            Condition.eq(Article::getOwnerId, 1),
            Condition.ne(Article::getStatus, "archived")
        );

        Policy policy = new PolicyBuilder()
            .allow(update, article, conditions)
            .build();

        Article validArticle = new Article(1, 1, "published", 100, List.of());
        assertTrue(policy.can(update, article.wrap(validArticle)));

        Article wrongOwner = new Article(1, 2, "published", 100, List.of());
        assertFalse(policy.can(update, article.wrap(wrongOwner)));

        Article archived = new Article(1, 1, "archived", 100, List.of());
        assertFalse(policy.can(update, article.wrap(archived)));
    }

    @Test
    public void testOrCondition() {
        Subject<Article> article = SubjectFactory.create("Article");
        Action publish = ActionFactory.create("Publish");

        Map<String, Object> conditions = Condition.or(
            Condition.eq(Article::getOwnerId, 1),
            Condition.eq(Article::getOwnerId, 2)
        );

        Policy policy = new PolicyBuilder()
            .allow(publish, article, conditions)
            .build();

        Article owner1 = new Article(1, 1, "draft", 0, List.of());
        assertTrue(policy.can(publish, article.wrap(owner1)));

        Article owner2 = new Article(2, 2, "draft", 0, List.of());
        assertTrue(policy.can(publish, article.wrap(owner2)));

        Article owner3 = new Article(3, 3, "draft", 0, List.of());
        assertFalse(policy.can(publish, article.wrap(owner3)));
    }

    @Test
    public void testAndCondition2() {
        Subject<Article> article = SubjectFactory.create("Article");
        Action update = ActionFactory.create("Update");

        Map<String, Object> conditions = Condition.and(
            Condition.eq(Article::getOwnerId, 1),
            Condition.ne(Article::getStatus, "archived")
        );

        Policy policy = new PolicyBuilder()
            .allow(update, article, conditions)
            .build();

        Article validArticle = new Article(1, 1, "published", 100, List.of());
        assertTrue(policy.can(update, article.wrap(validArticle)));
    }

    @Test
    public void testHasCondition() {
        Subject<Article> article = SubjectFactory.create("Article");
        Action update = ActionFactory.create("Update");

        Policy policy = new PolicyBuilder()
            .allow(update, article, Condition.has(Article::getTags, "featured"))
            .build();

        Article tagged = new Article(1, 1, "published", 100, List.of("featured", "news"));
        assertTrue(policy.can(update, article.wrap(tagged)));

        Article untagged = new Article(1, 1, "published", 100, List.of("news"));
        assertFalse(policy.can(update, article.wrap(untagged)));
    }

    @Test
    public void testExplicitFieldCondition() {
        Subject<Map<String, Object>> record = SubjectFactory.create("Record");
        Action update = ActionFactory.create("Update");

        Policy policy = new PolicyBuilder()
            .allow(update, record, Condition.field("$type", Map.of("$eq", "invoice")))
            .build();

        assertTrue(policy.can(update, record.wrap(Map.of("$type", "invoice"))));
        assertFalse(policy.can(update, record.wrap(Map.of("$type", "receipt"))));
    }

    @Test
    public void testCustomOperatorHelper() {
        Subject<Article> article = SubjectFactory.create("Article");
        Action delete = ActionFactory.create("Delete");
        Operator hasRole = Operator.of("$hasRole", (s, v, ctx) -> "admin".equals(v));

        Policy policy = new PolicyBuilder(List.of(hasRole))
            .allow(delete, article, Condition.op("$hasRole", "admin"))
            .build();

        Article data = new Article(1, 1, "published", 100, List.of());
        assertTrue(policy.can(delete, article.wrap(data)));
    }
}
