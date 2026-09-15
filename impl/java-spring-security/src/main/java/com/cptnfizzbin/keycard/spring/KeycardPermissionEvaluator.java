package com.cptnfizzbin.keycard.spring;

import com.cptnfizzbin.keycard.action.Action;
import com.cptnfizzbin.keycard.action.ActionFactory;
import com.cptnfizzbin.keycard.policy.Policy;
import com.cptnfizzbin.keycard.subject.Subject;
import org.springframework.security.access.PermissionEvaluator;
import org.springframework.security.core.Authentication;

import java.io.Serializable;

/**
 * Bridges Spring Security's {@code hasPermission(...)} SpEL function to a
 * KeyCard {@link Policy}. Register one instance as the
 * {@code PermissionEvaluator} on a {@code MethodSecurityExpressionHandler}
 * and use it from method security annotations:
 *
 * <pre>{@code
 * @PreAuthorize("hasPermission(#article, 'Update')")
 * void updateArticle(Article article) { ... }
 *
 * @PreAuthorize("hasPermission(#id, 'Article', 'Delete')")
 * void deleteArticle(Long id) { ... }
 * }</pre>
 *
 * The {@code permission} argument becomes a KeyCard {@link Action} name
 * verbatim (via {@code toString()}), so it should match an action name
 * used in your PolicyDefinitions (e.g. {@code "Update"}, not
 * {@code "UPDATE"}, if that's what your policies declare).
 */
public final class KeycardPermissionEvaluator implements PermissionEvaluator {
    private final KeycardPolicySource policySource;
    private final KeycardSubjectResolver subjectResolver;

    public KeycardPermissionEvaluator(KeycardPolicySource policySource) {
        this(policySource, new DefaultSubjectResolver());
    }

    public KeycardPermissionEvaluator(KeycardPolicySource policySource, KeycardSubjectResolver subjectResolver) {
        this.policySource = policySource;
        this.subjectResolver = subjectResolver;
    }

    @Override
    public boolean hasPermission(Authentication authentication, Object targetDomainObject, Object permission) {
        if (authentication == null || targetDomainObject == null || permission == null) return false;

        Policy policy = policySource.policyFor(authentication);
        Subject<Object> subject = subjectResolver.resolve(targetDomainObject);
        Action<String> action = ActionFactory.create(permission.toString());
        return policy.can(action, subject);
    }

    @Override
    public boolean hasPermission(Authentication authentication, Serializable targetId, String targetType, Object permission) {
        if (authentication == null || targetId == null || targetType == null || permission == null) return false;

        Policy policy = policySource.policyFor(authentication);
        Subject<Object> subject = subjectResolver.resolve(targetId, targetType);
        Action<String> action = ActionFactory.create(permission.toString());
        return policy.can(action, subject);
    }
}
