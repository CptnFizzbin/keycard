package com.cptnfizzbin.keycard.examples.vision.realbackend;

import com.cptnfizzbin.keycard.action.Action;
import com.cptnfizzbin.keycard.errors.PolicyException;
import com.cptnfizzbin.keycard.subject.Subject;

/**
 * website/docs-java/vision-real-backend.md's {@code policy/PermissionService.java},
 * with the Spring {@code @Service}/{@code ResponseStatusException} wrapping
 * left to whatever web framework a real app uses - here it just lets
 * {@link PolicyException} propagate.
 */
public class PermissionService {
    public static void require(PolicyClaims claims, Action action, Subject<?, ?> subject) throws PolicyException {
        AppPolicyBuilder.buildFor(claims).require(action, subject);
    }
}
