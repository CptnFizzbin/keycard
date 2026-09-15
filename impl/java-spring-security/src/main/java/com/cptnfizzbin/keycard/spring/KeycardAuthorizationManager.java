package com.cptnfizzbin.keycard.spring;

import com.cptnfizzbin.keycard.action.Action;
import com.cptnfizzbin.keycard.action.ActionFactory;
import com.cptnfizzbin.keycard.policy.Policy;
import com.cptnfizzbin.keycard.subject.Subject;
import com.cptnfizzbin.keycard.subject.SubjectFactory;
import org.springframework.security.authorization.AuthorizationDecision;
import org.springframework.security.authorization.AuthorizationManager;
import org.springframework.security.core.Authentication;
import org.springframework.security.web.access.intercept.RequestAuthorizationContext;

import java.util.function.Supplier;

/**
 * Secures a route in the filter chain (before any domain object is
 * loaded) with a fixed action/subject pair, e.g.:
 *
 * <pre>{@code
 * http.authorizeHttpRequests(auth -> auth
 *     .requestMatchers("/articles/**")
 *         .access(new KeycardAuthorizationManager(policySource, "Read", "Article"))
 * );
 * }</pre>
 *
 * There's no domain instance at this point in the request lifecycle, so
 * this is necessarily a bare, no-instance check (SPEC_V1-0-0.md
 * EC-7/EC-9): a Policy rule with Conditions can never match it. Use
 * {@link KeycardPermissionEvaluator} instead once a specific instance is
 * in hand (e.g. inside the handler method, via {@code @PreAuthorize}).
 */
public final class KeycardAuthorizationManager implements AuthorizationManager<RequestAuthorizationContext> {
    private final KeycardPolicySource policySource;
    private final Action<String> action;
    private final Subject<Object> subject;

    public KeycardAuthorizationManager(KeycardPolicySource policySource, String action, String subjectName) {
        this.policySource = policySource;
        this.action = ActionFactory.create(action);
        this.subject = SubjectFactory.create(subjectName);
    }

    @Override
    public AuthorizationDecision check(Supplier<Authentication> authentication, RequestAuthorizationContext context) {
        Policy policy = policySource.policyFor(authentication.get());
        return new AuthorizationDecision(policy.can(action, subject));
    }
}
