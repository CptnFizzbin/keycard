package com.cptnfizzbin.keycard.spring;

import com.cptnfizzbin.keycard.subject.Subject;

import java.io.Serializable;

/**
 * Maps the two shapes {@link org.springframework.security.access.PermissionEvaluator}
 * hands over into a KeyCard {@link Subject}:
 *
 * <ul>
 *   <li>{@link #resolve(Object)} - a loaded domain object, as passed to
 *       {@code hasPermission(Authentication, Object, Object)} (e.g.
 *       {@code hasPermission(#article, 'Update')}). Wraps it as a
 *       Subject instance, so instance-aware Conditions in the Policy
 *       (e.g. {@code owner_id}) can evaluate against it.</li>
 *   <li>{@link #resolve(Serializable, String)} - a bare id + type, as
 *       passed to {@code hasPermission(Authentication, Serializable,
 *       String, Object)} (e.g. {@code hasPermission(#id, 'Article',
 *       'Delete')}). No instance is loaded here, so this is necessarily a
 *       bare, no-instance check (SPEC_V1-0-0.md EC-7/EC-9): a rule with
 *       Conditions can never match it.</li>
 * </ul>
 */
public interface KeycardSubjectResolver {
    Subject<Object> resolve(Object domainObject);

    Subject<Object> resolve(Serializable targetId, String targetType);
}
