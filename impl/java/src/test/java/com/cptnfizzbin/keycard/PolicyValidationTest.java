package com.cptnfizzbin.keycard;

import com.cptnfizzbin.keycard.action.Action;
import com.cptnfizzbin.keycard.action.ActionFactory;
import com.cptnfizzbin.keycard.builder.PolicyBuilder;
import com.cptnfizzbin.keycard.conditions.Conditions;
import com.cptnfizzbin.keycard.conditions.Operator;
import com.cptnfizzbin.keycard.errors.PolicyArgumentException;
import com.cptnfizzbin.keycard.errors.PolicyLoadException;
import com.cptnfizzbin.keycard.errors.PolicyVersionException;
import com.cptnfizzbin.keycard.policy.Policy;
import com.cptnfizzbin.keycard.policy.PolicyDefinition;
import com.cptnfizzbin.keycard.subject.Subject;
import com.cptnfizzbin.keycard.subject.SubjectFactory;
import com.cptnfizzbin.keycard.version.KeyCardVersion;
import org.junit.Test;
import org.semver4j.Semver;

import java.util.List;
import java.util.Map;

import static org.junit.Assert.*;

/**
 * Construction-time validation required by SPEC_V0.md but not covered by the allow/deny-outcome-only v1 conformance suite (see test/fixtures/v1/README.md's Scope section).
 */
public class PolicyValidationTest {
    @Test
    public void throwsPolicyVersionExceptionForAnUnsupportedMajorVersion() {
        Semver nextMajor = KeyCardVersion.KEYCARD_POLICY_VERSION.nextMajor();
        assertThrows(PolicyVersionException.class, () ->
            Policy.from(new PolicyDefinition().version(nextMajor.toString())));
    }

    @Test
    public void throwsPolicyVersionExceptionForAMinorNewerThanWhatsSupported() {
        Semver nextMinor = KeyCardVersion.KEYCARD_POLICY_VERSION.nextMinor();
        assertThrows(PolicyVersionException.class, () ->
            Policy.from(new PolicyDefinition().version(nextMinor.toString())));
    }

    @Test
    public void ignoresPatchWhenDecidingCompatibility() {
        Semver nextPatch = KeyCardVersion.KEYCARD_POLICY_VERSION.nextPatch();
        Policy.from(new PolicyDefinition().version(nextPatch.toString())); // should not throw
    }

    @Test
    public void throwsPolicyLoadExceptionForAMalformedRuleTuple() {
        assertThrows(PolicyLoadException.class, () ->
            Policy.from(new PolicyDefinition().rules(List.of(
                new PolicyDefinition.Rule("maybe", "Read", "Article", null)
            ))));
    }

    @Test
    public void throwsPolicyLoadExceptionForARuleWildcardedOnBothSidesCarryingACondition() {
        assertThrows(PolicyLoadException.class, () ->
            Policy.from(new PolicyDefinition()
                .rules(List.of(
                    new PolicyDefinition.Rule("allow", "_ANY_", "_ANY_", Conditions.op("owner_id", 1))
                ))
            ));
    }

    @Test
    public void throwsPolicyLoadExceptionWhenARulesActionIsntCoveredByADeclaredCatalog() {
        PolicyDefinition.Meta meta = new PolicyDefinition.Meta().actions(List.of("Read"));

        assertThrows(PolicyLoadException.class, () ->
            Policy.from(new PolicyDefinition()
                .meta(meta)
                .rules(List.of(
                    new PolicyDefinition.Rule("allow", "Write", "Article", null)
                ))
            ));
    }

    @Test
    public void throwsPolicyLoadExceptionWhenARuleUsesACustomOperatorOutsideADeclaredCatalog() {
        PolicyDefinition.Meta meta = new PolicyDefinition.Meta().operators(List.of("$hasRole"));

        assertThrows(PolicyLoadException.class, () ->
            Policy.from(new PolicyDefinition()
                .meta(meta)
                .rules(List.of(
                    new PolicyDefinition.Rule("allow", "Read", "Article", Conditions.op("$isAdmin", true))
                ))
            ));
    }

    // --- Issue 3: operator registry collisions (SPEC_V0.md §3.2.3, EC-16) ---

    @Test
    public void throwsPolicyLoadExceptionWhenACustomOperatorCollidesWithABuiltin() {
        assertThrows(PolicyLoadException.class, () ->
            Policy.from(new PolicyDefinition(),
                List.of(Operator.of("$eq", (s, v, ctx) -> true))));
    }

    @Test
    public void throwsPolicyLoadExceptionWhenTwoCustomOperatorsCollideWithEachOther() {
        assertThrows(PolicyLoadException.class, () ->
            Policy.from(new PolicyDefinition(), List.of(
                Operator.of("$hasRole", (s, v, ctx) -> true),
                Operator.of("$hasRole", (s, v, ctx) -> false)
            )));
    }

    // --- Issue 4: meta.operators promotes "cataloged but never registered" to a construction-time throw (EC-15) ---

    @Test
    public void throwsPolicyLoadExceptionWhenMetaOperatorsDeclaresANameNothingIsRegisteredFor() {
        PolicyDefinition.Meta meta = new PolicyDefinition.Meta().operators(List.of("$hasRole"));

        // Unlike EC-13 above, this throws even though no rule references
        // $hasRole at all - meta.operators' registration requirement is
        // checked in full at construction time, not merely for names rules
        // actually use.
        assertThrows(PolicyLoadException.class, () ->
            Policy.from(new PolicyDefinition().meta(meta)));
    }

    @Test
    public void metaOperatorsIsSatisfiedByABuiltinName() {
        PolicyDefinition.Meta meta = new PolicyDefinition.Meta().operators(List.of("$eq"));

        Policy.from(new PolicyDefinition().meta(meta)); // should not throw
    }

    @Test
    public void metaOperatorsIsSatisfiedByARegisteredCustomOperator() {
        PolicyDefinition.Meta meta = new PolicyDefinition.Meta().operators(List.of("$hasRole"));

        Policy.from(
            new PolicyDefinition().meta(meta),
            List.of(Operator.of("$hasRole", (s, v, ctx) -> true))
        ); // should not throw
    }

    // --- Issue 5: meta.anyAction/meta.anySubject four-way dispatch (SPEC_V0.md §3.2.1) ---

    @Test
    public void falseDisablesTheActionWildcardJustLikeNull() {
        PolicyDefinition.Meta meta = new PolicyDefinition.Meta().anyAction(false);

        Policy policy = Policy.from(new PolicyDefinition().meta(meta).rules(List.of(
            new PolicyDefinition.Rule("allow", "_ANY_", "Article", null)
        )));

        // With the wildcard disabled, "_ANY_" is just an ordinary, literal
        // action name - it does not match "Read".
        assertTrue(policy.cannot(ActionFactory.create("Read"), SubjectFactory.create("Article")));
        assertTrue(policy.can(ActionFactory.create("_ANY_"), SubjectFactory.create("Article")));
    }

    // --- PolicyBuilder derives meta.actions/subjects/operators from usage; only the wildcard tokens are ever declared explicitly ---

    @Test
    public void buildDefDerivesActionsSubjectsAndOperatorsFromWhatWasActuallyUsed() {
        Subject<?> article = SubjectFactory.create("Article");
        Subject<?> user = SubjectFactory.create("User");
        Action read = ActionFactory.create("Read");
        Action update = ActionFactory.create("Update");
        Operator hasRole = Operator.of("$hasRole", (s, v, ctx) -> true);

        PolicyDefinition def = new PolicyBuilder(List.of(hasRole))
            .allow(read, article)
            .allow(update, user, Conditions.op("$hasRole", "admin"))
            .buildDef();

        assertEquals(List.of("Read", "Update"), def.meta().actions());
        assertEquals(List.of("Article", "User"), def.meta().subjects());
        assertEquals(List.of("$hasRole"), def.meta().operators());
    }

    @Test
    public void buildDefLeavesWildcardTokensUndeclaredByDefault() {
        PolicyDefinition def = new PolicyBuilder()
            .allow(ActionFactory.create("Read"), SubjectFactory.create("Article"))
            .buildDef();

        // Undeclared -> null on Meta, so Wildcards.effectiveAnyAction/
        // effectiveAnySubject fall back to the "_ANY_" default - a
        // PolicyBuilder() with no wildcard args MUST NOT come out as
        // "explicitly disabled" (that's what WildcardToken.of(null) means).
        assertEquals(null, def.meta().anyAction());
        assertEquals(null, def.meta().anySubject());
    }

    @Test
    public void wildcardOnlyConstructorDeclaresJustTheTokensRequested() {
        Policy policy = new PolicyBuilder("*", false)
            .allow(ActionFactory.create("*"), SubjectFactory.create("Article"))
            .allow(ActionFactory.create("Read"), SubjectFactory.create("*"))
            .build();

        // "*" is now the action wildcard token: a rule naming it as its
        // action matches any incoming action.
        assertTrue(policy.can(ActionFactory.create("AnythingGoes"), SubjectFactory.create("Article")));

        // The subject wildcard is disabled (false): a rule's literal "*"
        // subject only matches an incoming subject also literally named "*".
        assertFalse(policy.can(ActionFactory.create("Read"), SubjectFactory.create("AnySubjectName")));
        assertTrue(policy.can(ActionFactory.create("Read"), SubjectFactory.create("*")));
    }

    @Test
    public void wildcardOnlyConstructorStillCatchesEc6AtAddRuleTime() {
        assertThrows(PolicyArgumentException.class, () ->
            new PolicyBuilder("*", "*")
                .allow(ActionFactory.create("*"), SubjectFactory.create("*"), Conditions.op("owner_id", 1)));
    }

    // --- KeycardConfig, accepted by both PolicyBuilder and Policy ---

    @Test
    public void keycardConfigActionsAndSubjectsAreFoldedIntoMetaAlongsideWhatUsageDerives() {
        KeycardConfig config = KeycardConfig.builder()
            .actions(List.of(ActionFactory.create("Delete")))
            .subjects(List.of(SubjectFactory.create("Comment")))
            .build();

        PolicyDefinition def = new PolicyBuilder(config)
            .allow(ActionFactory.create("Read"), SubjectFactory.create("Article"))
            .buildDef();

        assertEquals(List.of("Read", "Delete"), def.meta().actions());
        assertEquals(List.of("Article", "Comment"), def.meta().subjects());
    }

    @Test
    public void keycardConfigOperatorsIsUsedByPolicyBuilder() {
        Operator hasRole = Operator.of("$hasRole", (s, v, ctx) -> true);
        KeycardConfig config = KeycardConfig.builder().operators(List.of(hasRole)).build();
        Subject<Object> article = SubjectFactory.create("Article");

        Policy policy = new PolicyBuilder(config)
            .allow(ActionFactory.create("Read"), article, Map.of("$hasRole", "admin"))
            .build();

        assertTrue(policy.can(ActionFactory.create("Read"), article.wrap(new Object())));
    }

    @Test
    public void keycardConfigLeavesWildcardTokensUndeclaredByDefault() {
        PolicyDefinition def = new PolicyBuilder(KeycardConfig.builder().build())
            .allow(ActionFactory.create("Read"), SubjectFactory.create("Article"))
            .buildDef();

        assertEquals(null, def.meta().anyAction());
        assertEquals(null, def.meta().anySubject());
    }

    @Test
    public void keycardConfigAnyActionAndAnySubjectDeclareTheWildcardTokens() {
        KeycardConfig config = KeycardConfig.builder().anyAction("*").anySubject(false).build();

        Policy policy = new PolicyBuilder(config)
            .allow(ActionFactory.create("*"), SubjectFactory.create("Article"))
            .allow(ActionFactory.create("Read"), SubjectFactory.create("*"))
            .build();

        // "*" is now the action wildcard token: a rule naming it as its
        // action matches any incoming action.
        assertTrue(policy.can(ActionFactory.create("AnythingGoes"), SubjectFactory.create("Article")));

        // The subject wildcard is disabled (false): a rule's literal "*"
        // subject only matches an incoming subject also literally named "*".
        assertFalse(policy.can(ActionFactory.create("Read"), SubjectFactory.create("AnySubjectName")));
        assertTrue(policy.can(ActionFactory.create("Read"), SubjectFactory.create("*")));
    }
}
