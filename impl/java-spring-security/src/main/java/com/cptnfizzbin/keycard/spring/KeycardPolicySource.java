package com.cptnfizzbin.keycard.spring;

import com.cptnfizzbin.keycard.policy.Policy;
import org.springframework.security.core.Authentication;

/**
 * Resolves the {@link Policy} that applies to an authenticated caller.
 * There is no single "current policy" in Spring Security the way there's a
 * single {@code Authentication} - how claims become a {@code Policy} (a
 * {@code PolicyBuilder} run over JWT claims, a lookup keyed by role, a
 * cache in front of a database) is entirely application-specific, so
 * {@link KeycardPermissionEvaluator} and {@link KeycardAuthorizationManager}
 * both depend on this instead of building a {@code Policy} themselves.
 */
@FunctionalInterface
public interface KeycardPolicySource {
    Policy policyFor(Authentication authentication);
}
