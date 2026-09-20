package com.cptnfizzbin.keycard;

import com.cptnfizzbin.keycard.action.Action;
import com.cptnfizzbin.keycard.action.ActionCatalog;
import com.cptnfizzbin.keycard.builder.PolicyBuilder;
import com.cptnfizzbin.keycard.conditions.Condition;
import com.cptnfizzbin.keycard.conditions.Operator;
import com.cptnfizzbin.keycard.conditions.OperatorCatalog;
import com.cptnfizzbin.keycard.errors.PolicyArgumentException;
import com.cptnfizzbin.keycard.errors.PolicyLoadException;
import com.cptnfizzbin.keycard.errors.PolicyVersionException;
import com.cptnfizzbin.keycard.policy.Policy;
import com.cptnfizzbin.keycard.policy.PolicyDefinition;
import com.cptnfizzbin.keycard.subject.Subject;
import com.cptnfizzbin.keycard.subject.SubjectCatalog;
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
    private static KeycardConfig withOperators(Operator... operators) {
        return new KeycardConfig().operators(new OperatorCatalog().addAll(List.of(operators)));
    }

    @Test
    public void throwsPolicyVersionExceptionForAnUnsupportedMajorVersion() {
        Semver nextMajor = KeyCardVersion.KEYCARD_POLICY_VERSION.nextMajor();
        assertThrows(PolicyVersionException.class, () ->
            new Policy(new PolicyDefinition().version(nextMajor.toString())));
    }

    @Test
    public void throwsPolicyVersionExceptionForAMinorNewerThanWhatsSupported() {
        Semver nextMinor = KeyCardVersion.KEYCARD_POLICY_VERSION.nextMinor();
        assertThrows(PolicyVersionException.class, () ->
            new Policy(new PolicyDefinition().version(nextMinor.toString())));
    }

    @Test
    public void ignoresPatchWhenDecidingCompatibility() {
        Semver nextPatch = KeyCardVersion.KEYCARD_POLICY_VERSION.nextPatch();
        new Policy(new PolicyDefinition().version(nextPatch.toString())); // should not throw
    }

    @Test
    public void throwsPolicyLoadExceptionForAMalformedRuleTuple() {
        assertThrows(PolicyLoadException.class, () ->
            new Policy(new PolicyDefinition().rules(List.of(
                new PolicyDefinition.Rule("maybe", "Read", "Article", null)
            ))));
    }

    @Test
    public void throwsPolicyLoadExceptionForARuleWildcardedOnBothSidesCarryingACondition() {
        assertThrows(PolicyLoadException.class, () -> {
            PolicyDefinition policyDef = new PolicyDefinition();

            policyDef.rules()
                .add(new PolicyDefinition.Rule("allow", "_ANY_", "_ANY_", Condition.op("owner_id", 1).toMap()));

            new Policy(policyDef);
        });
    }

    @Test
    public void throwsPolicyLoadExceptionWhenARulesActionIsntCoveredByADeclaredCatalog() {
        PolicyDefinition.Meta meta = new PolicyDefinition.Meta().actions(List.of("Read"));

        assertThrows(PolicyLoadException.class, () ->
            new Policy(new PolicyDefinition()
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
            new Policy(new PolicyDefinition()
                .meta(meta)
                .rules(List.of(
                    new PolicyDefinition.Rule("allow", "Read", "Article", Condition.op("$isAdmin", true).toMap())
                ))
            ));
    }

    // --- Issue 3: operator registry collisions ---

    @Test
    public void throwsPolicyLoadExceptionWhenACustomOperatorCollidesWithABuiltin() {
        assertThrows(PolicyLoadException.class, () ->
            new Policy(new PolicyDefinition(),
                withOperators(Operator.of("$eq", (s, v, ctx) -> true))));
    }

    @Test
    public void throwsPolicyLoadExceptionWhenTwoCustomOperatorsCollideWithEachOther() {
        assertThrows(PolicyLoadException.class, () ->
            new Policy(new PolicyDefinition(), withOperators(
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
        // checked in full when loading a policy, not merely for names rules
        // actually use.
        assertThrows(PolicyLoadException.class, () ->
            new Policy(new PolicyDefinition().meta(meta)));
    }

    @Test
    public void metaOperatorsIsSatisfiedByABuiltinName() {
        PolicyDefinition.Meta meta = new PolicyDefinition.Meta().operators(List.of("$eq"));

        new Policy(new PolicyDefinition().meta(meta)); // should not throw
    }

    @Test
    public void metaOperatorsIsSatisfiedByARegisteredCustomOperator() {
        PolicyDefinition.Meta meta = new PolicyDefinition.Meta().operators(List.of("$hasRole"));

        new Policy(
            new PolicyDefinition().meta(meta),
            withOperators(Operator.of("$hasRole", (s, v, ctx) -> true))
        ); // should not throw
    }

    // --- Issue 5: meta.anyAction/meta.anySubject four-way dispatch ---

    @Test
    public void falseDisablesTheActionWildcardJustLikeNull() {
        PolicyDefinition.Meta meta = new PolicyDefinition.Meta().anyAction(false);

        Policy policy = new Policy(new PolicyDefinition().meta(meta).rules(List.of(
            new PolicyDefinition.Rule("allow", "_ANY_", "Article", null)
        )));

        // With the wildcard disabled, "_ANY_" is just an ordinary, literal
        // action name - it does not match "Read".
        assertTrue(policy.cannot(new Action("Read"), new Subject<>("Article")));
        assertTrue(policy.can(new Action("_ANY_"), new Subject<>("Article")));
    }

    // --- PolicyBuilder derives meta.actions/subjects/operators from usage; only the wildcard tokens are ever declared explicitly ---

    @Test
    public void buildDefDerivesActionsSubjectsAndOperatorsFromWhatWasActuallyUsed() {
        Subject<?> article = new Subject<>("Article");
        Subject<?> user = new Subject<>("User");
        Action read = new Action("Read");
        Action update = new Action("Update");
        Operator hasRole = Operator.of("$hasRole", (s, v, ctx) -> true);

        PolicyDefinition def = new PolicyBuilder(withOperators(hasRole))
            .allow(read, article)
            .allow(update, user, Condition.op("$hasRole", "admin"))
            .buildDef();

        assertEquals(List.of("Read", "Update"), def.meta().actions());
        assertEquals(List.of("Article", "User"), def.meta().subjects());
        assertEquals(List.of("$hasRole"), def.meta().operators());
    }

    @Test
    public void buildDefLeavesWildcardTokensUndeclaredByDefault() {
        PolicyDefinition def = new PolicyBuilder()
            .allow(new Action("Read"), new Subject<>("Article"))
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
        Policy policy = new PolicyBuilder(new KeycardConfig().anyAction(new Action("*")).anySubject(null))
            .allow(new Action("*"), new Subject<>("Article"))
            .allow(new Action("Read"), new Subject<>("*"))
            .build();

        // "*" is now the action wildcard token: a rule naming it as its
        // action matches any incoming action.
        assertTrue(policy.can(new Action("AnythingGoes"), new Subject<>("Article")));

        // The subject wildcard is disabled (false): a rule's literal "*"
        // subject only matches an incoming subject also literally named "*".
        assertFalse(policy.can(new Action("Read"), new Subject<>("AnySubjectName")));
        assertTrue(policy.can(new Action("Read"), new Subject<>("*")));
    }

    @Test
    public void wildcardOnlyConstructorStillCatchesEc6AtAddRuleTime() {
        assertThrows(PolicyArgumentException.class, () ->
            new PolicyBuilder(new KeycardConfig().anyAction(new Action("*")).anySubject(new Subject<>("*")))
                .allow(new Action("*"), new Subject<>("*"), Condition.op("owner_id", 1)));
    }

    // --- KeycardConfig, accepted by both PolicyBuilder and Policy ---

    @Test
    public void keycardConfigActionsAndSubjectsAreFoldedIntoMetaAlongsideWhatUsageDerives() {
        KeycardConfig config = new KeycardConfig()
            .actions(new ActionCatalog().add(new Action("Delete")))
            .subjects(new SubjectCatalog().add(new Subject<>("Comment")));

        PolicyDefinition def = new PolicyBuilder(config)
            .allow(new Action("Read"), new Subject<>("Article"))
            .buildDef();

        assertEquals(List.of("Read", "Delete"), def.meta().actions());
        assertEquals(List.of("Article", "Comment"), def.meta().subjects());
    }

    @Test
    public void keycardConfigOperatorsIsUsedByPolicyBuilder() {
        Operator hasRole = Operator.of("$hasRole", (s, v, ctx) -> true);
        KeycardConfig config = withOperators(hasRole);
        Subject<Object> article = new Subject<>("Article");

        Policy policy = new PolicyBuilder(config)
            .allow(new Action("Read"), article, Condition.op("$hasRole", "admin"))
            .build();

        assertTrue(policy.can(new Action("Read"), article.wrap(new Object())));
    }

    @Test
    public void keycardConfigLeavesWildcardTokensUndeclaredByDefault() {
        PolicyDefinition def = new PolicyBuilder(new KeycardConfig())
            .allow(new Action("Read"), new Subject<>("Article"))
            .buildDef();

        assertNull(def.meta().anyAction());
        assertNull(def.meta().anySubject());
    }

    @Test
    public void keycardConfigAnyActionAndAnySubjectDeclareTheWildcardTokens() {
        Action AnyAction = new Action("*");
        Subject<Object> AnySubject = new Subject<>("*");

        KeycardConfig config = new KeycardConfig()
            .anyAction(AnyAction)
            .anySubject(AnySubject);

        Policy policy = new PolicyBuilder(config)
            .allow(AnyAction, new Subject<>("Article"))
            .allow(new Action("Read"), AnySubject)
            .build();

        // "*" is now the action wildcard token: a rule naming it as its
        // action matches any incoming action.
        assertTrue(policy.can(new Action("AnythingGoes"), new Subject<>("Article")));

        // The subject wildcard is disabled (false): a rule's literal "*"
        // subject only matches an incoming subject also literally named "*".
        assertFalse(policy.can(new Action("Read"), new Subject<>("AnySubjectName")));
        assertTrue(policy.can(new Action("Read"), new Subject<>("*")));
    }
}
