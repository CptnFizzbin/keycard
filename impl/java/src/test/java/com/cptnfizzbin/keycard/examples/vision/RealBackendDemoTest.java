package com.cptnfizzbin.keycard.examples.vision;

import com.cptnfizzbin.keycard.errors.PolicyException;
import com.cptnfizzbin.keycard.examples.vision.realbackend.*;
import com.cptnfizzbin.keycard.policy.Policy;
import org.junit.Test;

import java.time.Instant;
import java.time.temporal.ChronoUnit;

import static org.junit.Assert.*;

/**
 * Exercises website/docs-java/vision-real-backend.md's example end to end.
 */
public class RealBackendDemoTest {
    @Test
    public void ownersAndAdminsCanManageProjectsInTheirOrg() {
        PolicyClaims owner = new PolicyClaims("u1", "org1", Role.OWNER);
        Policy policy = AppPolicyBuilder.buildFor(owner);

        Project inOrgProject = new Project();
        inOrgProject.setOrgId("org1");
        inOrgProject.setOwnerId("u1");

        Project otherOrgProject = new Project();
        otherOrgProject.setOrgId("org2");
        otherOrgProject.setOwnerId("u2");

        Project archivedProject = new Project();
        archivedProject.setOrgId("org1");
        archivedProject.setArchivedAt(Instant.now());

        // Create/Update/Invite all carry the same in-org condition - a bare,
        // instance-less check can never satisfy a conditional rule (EC-7).
        assertFalse(policy.can(AppActions.Create, AppSubjects.Project));
        assertTrue(policy.can(AppActions.Create, AppSubjects.Project.from(inOrgProject)));
        assertTrue(policy.can(AppActions.Update, AppSubjects.Project.from(inOrgProject)));
        assertFalse(policy.can(AppActions.Update, AppSubjects.Project.from(otherOrgProject)));

        // Delete requires the project to be both in-org AND archived.
        assertFalse(policy.can(AppActions.Delete, AppSubjects.Project.from(inOrgProject)));
        assertTrue(policy.can(AppActions.Delete, AppSubjects.Project.from(archivedProject)));
    }

    @Test
    public void membersCanOnlyUpdateTheirOwnRecentTasks() {
        PolicyClaims member = new PolicyClaims("u1", "org1", Role.MEMBER);
        Policy policy = AppPolicyBuilder.buildFor(member);

        Task ownRecentTask = new Task();
        ownRecentTask.setAssigneeId("u1");
        ownRecentTask.setCreatedAt(Instant.now());

        Task ownStaleTask = new Task();
        ownStaleTask.setAssigneeId("u1");
        ownStaleTask.setCreatedAt(Instant.now().minus(60, ChronoUnit.DAYS));

        Task othersTask = new Task();
        othersTask.setAssigneeId("u2");
        othersTask.setCreatedAt(Instant.now());

        // .from(...) is called on the catalog-registered AppSubjects.Task
        // singleton itself - it's what carries the catalog-resolvable name
        // through wrap()/copy() (see Subject's class doc).
        assertTrue(policy.can(AppActions.Update, AppSubjects.Task.from(ownRecentTask, projectIn("org1"))));
        assertFalse(policy.can(AppActions.Update, AppSubjects.Task.from(ownStaleTask, projectIn("org1"))));
        assertFalse(policy.can(AppActions.Update, AppSubjects.Task.from(othersTask, projectIn("org1"))));

        // Members were never granted Create on Project at all.
        assertFalse(policy.can(AppActions.Create, AppSubjects.Project));

        assertThrows(PolicyException.class, () -> PermissionService.require(member, AppActions.Create, AppSubjects.Project));
    }

    private static Project projectIn(String orgId) {
        Project p = new Project();
        p.setOrgId(orgId);
        return p;
    }
}
