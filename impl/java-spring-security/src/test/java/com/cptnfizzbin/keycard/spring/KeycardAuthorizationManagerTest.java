package com.cptnfizzbin.keycard.spring;

import com.cptnfizzbin.keycard.action.ActionFactory;
import com.cptnfizzbin.keycard.builder.PolicyBuilder;
import com.cptnfizzbin.keycard.policy.Policy;
import com.cptnfizzbin.keycard.subject.SubjectFactory;
import org.junit.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.security.authentication.TestingAuthenticationToken;
import org.springframework.security.authorization.AuthorizationDecision;
import org.springframework.security.core.Authentication;
import org.springframework.security.web.access.intercept.RequestAuthorizationContext;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class KeycardAuthorizationManagerTest {
    @Test
    public void grantsWhenPolicyAllowsBareSubject() {
        Policy policy = new PolicyBuilder()
            .allow(ActionFactory.create("Read"), SubjectFactory.create("Article"))
            .build();
        KeycardPolicySource policySource = auth -> policy;
        KeycardAuthorizationManager manager = new KeycardAuthorizationManager(policySource, "Read", "Article");

        AuthorizationDecision decision = manager.check(
            () -> new TestingAuthenticationToken("alice", null),
            new RequestAuthorizationContext(new MockHttpServletRequest())
        );

        assertTrue(decision.isGranted());
    }

    @Test
    public void deniesWhenPolicyHasNoMatchingRule() {
        Policy policy = new PolicyBuilder().build();
        KeycardPolicySource policySource = auth -> policy;
        KeycardAuthorizationManager manager = new KeycardAuthorizationManager(policySource, "Read", "Article");

        AuthorizationDecision decision = manager.check(
            () -> new TestingAuthenticationToken("alice", null),
            new RequestAuthorizationContext(new MockHttpServletRequest())
        );

        assertFalse(decision.isGranted());
    }
}
