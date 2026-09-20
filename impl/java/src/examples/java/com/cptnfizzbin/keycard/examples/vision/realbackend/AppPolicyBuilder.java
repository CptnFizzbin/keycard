package com.cptnfizzbin.keycard.examples.vision.realbackend;

import com.cptnfizzbin.keycard.KeycardConfig;
import com.cptnfizzbin.keycard.builder.PolicyBuilder;
import com.cptnfizzbin.keycard.conditions.Condition;
import com.cptnfizzbin.keycard.policy.Policy;

/**
 * website/docs-java/vision-real-backend.md's {@code policy/AppPolicyBuilder.java}.
 * <p>
 * {@code AppPolicyBuilder} rebuilds the policy on every call - Policy
 * Claims come from the request's own JWT/session, so nothing long-lived
 * can safely cache across users. Only the static {@code CONFIG} is shared.
 */
public class AppPolicyBuilder {
    public static final KeycardConfig CONFIG = new KeycardConfig()
        .actions(AppActions.catalog)
        .subjects(AppSubjects.catalog)
        .operators(AppOperators.catalog)
        /*
        Enables the ability to perform fail-fast checks while loading a
        policy during development. When added, the library is able to
        confirm that all actions, subjects, and operators needed for the
        policy are registered.
        */
        .emitMeta(true) // default: true - flip to Environment.isDevelopment() in a real app
        /*
        Enables the ability for shared test cases to be added to the policy
        file to allow for confirmations that two or more languages are
        operating with the same permissions.
        */
        .emitTests(false); // default: false

    public static Policy buildFor(PolicyClaims claims) {
        PolicyBuilder builder = new PolicyBuilder(CONFIG)
            .allow(AppActions.Read, AppSubjects.Project,
                Condition.<ProjectSubject.Claims, String>field(ProjectSubject.Claims::orgId).eq(claims.orgId()))
            .allow(AppActions.Read, AppSubjects.Task,
                Condition.<TaskSubject.Claims, String>field(TaskSubject.Claims::orgId).eq(claims.orgId()));

        if (claims.role() == Role.OWNER || claims.role() == Role.ADMIN) {
            Condition<ProjectSubject.Claims> inOrg =
                Condition.<ProjectSubject.Claims, String>field(ProjectSubject.Claims::orgId).eq(claims.orgId());
            Condition<ProjectSubject.Claims> isArchived =
                Condition.<ProjectSubject.Claims, Boolean>field(ProjectSubject.Claims::archived).eq(true);

            builder
                .allow(AppActions.Create, AppSubjects.Project, inOrg)
                .allow(AppActions.Update, AppSubjects.Project, inOrg)
                .allow(AppActions.Invite, AppSubjects.Project, inOrg)
                .allow(AppActions.Delete, AppSubjects.Project, Condition.where(
                    Condition.and(inOrg, isArchived)
                ));
        } else {
            // members can only touch tasks assigned to them, and only recent ones
            Condition<TaskSubject.Claims> inOrg =
                Condition.<TaskSubject.Claims, String>field(TaskSubject.Claims::orgId).eq(claims.orgId());
            Condition<TaskSubject.Claims> isAssignee =
                Condition.<TaskSubject.Claims, String>field(TaskSubject.Claims::assigneeId).eq(claims.userId());
            Condition<TaskSubject.Claims> isRecent =
                Condition.<TaskSubject.Claims, java.time.Instant>field(TaskSubject.Claims::createdAt).op("$withinDays", 30);

            builder.allow(AppActions.Update, AppSubjects.Task, Condition.where(
                Condition.and(inOrg, isAssignee, isRecent)
            ));
        }

        return builder.build();
    }
}
