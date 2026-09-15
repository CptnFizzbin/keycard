package com.cptnfizzbin.keycard.spring;

import com.cptnfizzbin.keycard.policy.Policy;
import org.springframework.security.core.Authentication;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Function;

/**
 * Wraps a {@link KeycardPolicySource} that's expensive to call (e.g. one
 * that runs a {@code PolicyBuilder} over a database-loaded role set) so a
 * {@code Policy} is built at most once per cache key, not once per
 * {@code hasPermission(...)} check. {@code Authentication} instances are
 * typically rebuilt per request (e.g. from a JWT), so callers supply a
 * stable {@code keyOf} - usually {@link Authentication#getName()} - rather
 * than caching on the {@code Authentication} instance itself.
 *
 * <p>This is an unbounded cache with no eviction or TTL: fine for a small,
 * fixed set of roles/tenants, but a caller whose key space grows unbounded
 * (e.g. one entry per user) should wrap {@link #policyFor} with an
 * eviction-aware cache (e.g. Caffeine) instead of using this class as-is.
 */
public final class CachingPolicySource implements KeycardPolicySource {
    private final KeycardPolicySource delegate;
    private final Function<Authentication, Object> keyOf;
    private final Map<Object, Policy> cache = new ConcurrentHashMap<>();

    public CachingPolicySource(KeycardPolicySource delegate, Function<Authentication, Object> keyOf) {
        this.delegate = delegate;
        this.keyOf = keyOf;
    }

    @Override
    public Policy policyFor(Authentication authentication) {
        Object key = keyOf.apply(authentication);
        return cache.computeIfAbsent(key, ignored -> delegate.policyFor(authentication));
    }
}
