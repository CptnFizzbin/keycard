package com.cptnfizzbin.keycard.spring;

import com.cptnfizzbin.keycard.builder.PolicyBuilder;
import com.cptnfizzbin.keycard.policy.Policy;
import org.junit.Test;
import org.springframework.security.authentication.TestingAuthenticationToken;
import org.springframework.security.core.Authentication;

import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertSame;

public class CachingPolicySourceTest {
    @Test
    public void buildsPolicyAtMostOncePerKey() {
        AtomicInteger builds = new AtomicInteger();
        KeycardPolicySource delegate = auth -> {
            builds.incrementAndGet();
            return new PolicyBuilder().build();
        };
        CachingPolicySource cached = new CachingPolicySource(delegate, Authentication::getName);

        Authentication first = new TestingAuthenticationToken("alice", null);
        Authentication second = new TestingAuthenticationToken("alice", null);
        Policy firstResult = cached.policyFor(first);
        Policy secondResult = cached.policyFor(second);

        assertSame(firstResult, secondResult);
        assertEquals(1, builds.get());
    }

    @Test
    public void buildsSeparatePoliciesForDifferentKeys() {
        AtomicInteger builds = new AtomicInteger();
        KeycardPolicySource delegate = auth -> {
            builds.incrementAndGet();
            return new PolicyBuilder().build();
        };
        CachingPolicySource cached = new CachingPolicySource(delegate, Authentication::getName);

        cached.policyFor(new TestingAuthenticationToken("alice", null));
        cached.policyFor(new TestingAuthenticationToken("bob", null));

        assertEquals(2, builds.get());
    }
}
