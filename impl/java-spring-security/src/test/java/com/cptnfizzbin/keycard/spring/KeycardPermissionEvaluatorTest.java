package com.cptnfizzbin.keycard.spring;

import com.cptnfizzbin.keycard.action.Action;
import com.cptnfizzbin.keycard.action.ActionFactory;
import com.cptnfizzbin.keycard.builder.PolicyBuilder;
import com.cptnfizzbin.keycard.policy.Policy;
import com.cptnfizzbin.keycard.subject.Subject;
import com.cptnfizzbin.keycard.subject.SubjectFactory;
import org.junit.Test;
import org.springframework.security.authentication.TestingAuthenticationToken;
import org.springframework.security.core.Authentication;

import java.util.Map;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class KeycardPermissionEvaluatorTest {
    static final class Article {
        final int ownerId;
        final String status;

        Article(int ownerId, String status) {
            this.ownerId = ownerId;
            this.status = status;
        }
    }

    private static KeycardPermissionEvaluator evaluatorFor(int ownerId) {
        Action<String> create = ActionFactory.create("Create");
        Action<String> update = ActionFactory.create("Update");
        Action<String> delete = ActionFactory.create("Delete");
        Subject<Article> article = SubjectFactory.create("Article");

        Policy policy = new PolicyBuilder()
            .allow(create, article)
            .allow(update, article, Map.of("ownerId", ownerId))
            .deny(delete, article, Map.of("status", Map.of("$not", "archived")))
            .build();

        KeycardPolicySource policySource = auth -> policy;
        return new KeycardPermissionEvaluator(policySource);
    }

    @Test
    public void grantsSchemaOnlyPermission() {
        KeycardPermissionEvaluator evaluator = evaluatorFor(1);
        Authentication auth = new TestingAuthenticationToken("alice", null);

        // No instance is needed - "Create" carries no conditions.
        assertTrue(evaluator.hasPermission(auth, new Article(1, "draft"), "Create"));
    }

    @Test
    public void grantsConditionalPermissionForOwnedInstance() {
        KeycardPermissionEvaluator evaluator = evaluatorFor(1);
        Authentication auth = new TestingAuthenticationToken("alice", null);

        assertTrue(evaluator.hasPermission(auth, new Article(1, "published"), "Update"));
    }

    @Test
    public void deniesConditionalPermissionForUnownedInstance() {
        KeycardPermissionEvaluator evaluator = evaluatorFor(1);
        Authentication auth = new TestingAuthenticationToken("alice", null);

        assertFalse(evaluator.hasPermission(auth, new Article(2, "published"), "Update"));
    }

    @Test
    public void deniesArchivedDeletion() {
        KeycardPermissionEvaluator evaluator = evaluatorFor(1);
        Authentication auth = new TestingAuthenticationToken("alice", null);

        assertFalse(evaluator.hasPermission(auth, new Article(1, "archived"), "Delete"));
    }

    @Test
    public void bareIdAndTypeCheckIsSchemaOnly() {
        KeycardPermissionEvaluator evaluator = evaluatorFor(1);
        Authentication auth = new TestingAuthenticationToken("alice", null);

        // No instance was loaded, so a conditional rule (Update, Delete)
        // can never match - only the unconditional "Create" rule can.
        assertTrue(evaluator.hasPermission(auth, 42L, "Article", "Create"));
        assertFalse(evaluator.hasPermission(auth, 42L, "Article", "Update"));
    }

    @Test
    public void deniesWhenAuthenticationIsNull() {
        KeycardPermissionEvaluator evaluator = evaluatorFor(1);
        assertFalse(evaluator.hasPermission(null, new Article(1, "draft"), "Create"));
    }
}
