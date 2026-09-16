package com.cptnfizzbin.keycard;

import org.junit.Test;

import com.cptnfizzbin.keycard.action.Action;
import com.cptnfizzbin.keycard.action.ActionFactory;
import com.cptnfizzbin.keycard.builder.PolicyBuilder;
import com.cptnfizzbin.keycard.conditions.Operator;
import com.cptnfizzbin.keycard.errors.PolicyArgumentException;
import com.cptnfizzbin.keycard.policy.Policy;
import com.cptnfizzbin.keycard.policy.PolicyDefinition;
import com.cptnfizzbin.keycard.errors.PolicyLoadException;
import com.cptnfizzbin.keycard.errors.PolicyVersionException;
import com.cptnfizzbin.keycard.subject.Subject;
import com.cptnfizzbin.keycard.subject.SubjectFactory;

import java.util.List;
import java.util.Map;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;

/** Construction-time validation required by SPEC_V1-0-0.md but not covered by the allow/deny-outcome-only v1 conformance suite (see test/fixtures/v1/README.md's Scope section). */
public class PolicyValidationTest {

    @Test
    public void throwsPolicyVersionExceptionForAnUnsupportedMajorVersion() {
        assertThrows(PolicyVersionException.class, () ->
            Policy.from(new PolicyDefinition("2.0.0", List.of())));
    }

    @Test
    public void throwsPolicyVersionExceptionForAMinorNewerThanWhatsSupported() {
        assertThrows(PolicyVersionException.class, () ->
            Policy.from(new PolicyDefinition("1.99.0", List.of())));
    }

    @Test
    public void ignoresPatchWhenDecidingCompatibility() {
        Policy.from(new PolicyDefinition("1.0.99", List.of())); // should not throw
    }

    @Test
    public void throwsPolicyLoadExceptionForAMalformedRuleTuple() {
        assertThrows(PolicyLoadException.class, () ->
            Policy.from(new PolicyDefinition("1.0.0", List.of(
                new PolicyDefinition.Rule("maybe", "Read", "Article", null)
            ))));
    }

    @Test
    public void throwsPolicyLoadExceptionForARuleWildcardedOnBothSidesCarryingACondition() {
        assertThrows(PolicyLoadException.class, () ->
            Policy.from(new PolicyDefinition("1.0.0", List.of(
                new PolicyDefinition.Rule("allow", "_ANY_", "_ANY_", Map.of("owner_id", 1))
            ))));
    }

    @Test
    public void throwsPolicyLoadExceptionWhenARulesActionIsntCoveredByADeclaredCatalog() {
        PolicyDefinition.Meta meta = PolicyDefinition.Meta.builder().actions(List.of("Read")).build();

        assertThrows(PolicyLoadException.class, () ->
            Policy.from(new PolicyDefinition("1.0.0", null, null, meta, List.of(
                new PolicyDefinition.Rule("allow", "Write", "Article", null)
            ))));
    }

    @Test
    public void throwsPolicyLoadExceptionWhenARuleUsesACustomOperatorOutsideADeclaredCatalog() {
        PolicyDefinition.Meta meta = PolicyDefinition.Meta.builder().operators(List.of("$hasRole")).build();

        assertThrows(PolicyLoadException.class, () ->
            Policy.from(new PolicyDefinition("1.0.0", null, null, meta, List.of(
                new PolicyDefinition.Rule("allow", "Read", "Article", Map.of("$isAdmin", true))
            ))));
    }

    // --- Issue 3: operator registry collisions (SPEC_V1-0-0.md §3.2.3, EC-16) ---

    @Test
    public void throwsPolicyLoadExceptionWhenACustomOperatorCollidesWithABuiltin() {
        assertThrows(PolicyLoadException.class, () ->
            Policy.from(new PolicyDefinition("1.0.0", List.of()),
                List.of(Operator.of("$eq", (s, v, ctx) -> true))));
    }

    @Test
    public void throwsPolicyLoadExceptionWhenTwoCustomOperatorsCollideWithEachOther() {
        assertThrows(PolicyLoadException.class, () ->
            Policy.from(new PolicyDefinition("1.0.0", List.of()), List.of(
                Operator.of("$hasRole", (s, v, ctx) -> true),
                Operator.of("$hasRole", (s, v, ctx) -> false)
            )));
    }

    // --- Issue 4: meta.operators promotes "cataloged but never registered" to a construction-time throw (EC-15) ---

    @Test
    public void throwsPolicyLoadExceptionWhenMetaOperatorsDeclaresANameNothingIsRegisteredFor() {
        PolicyDefinition.Meta meta = PolicyDefinition.Meta.builder().operators(List.of("$hasRole")).build();

        // Unlike EC-13 above, this throws even though no rule references
        // $hasRole at all - meta.operators' registration requirement is
        // checked in full at construction time, not merely for names rules
        // actually use.
        assertThrows(PolicyLoadException.class, () ->
            Policy.from(new PolicyDefinition("1.0.0", null, null, meta, List.of())));
    }

    @Test
    public void metaOperatorsIsSatisfiedByABuiltinName() {
        PolicyDefinition.Meta meta = PolicyDefinition.Meta.builder().operators(List.of("$eq")).build();

        Policy.from(new PolicyDefinition("1.0.0", null, null, meta, List.of())); // should not throw
    }

    @Test
    public void metaOperatorsIsSatisfiedByARegisteredCustomOperator() {
        PolicyDefinition.Meta meta = PolicyDefinition.Meta.builder().operators(List.of("$hasRole")).build();

        Policy.from(
            new PolicyDefinition("1.0.0", null, null, meta, List.of()),
            List.of(Operator.of("$hasRole", (s, v, ctx) -> true))
        ); // should not throw
    }

    // --- Issue 5: meta.anyAction/meta.anySubject four-way dispatch (SPEC_V1-0-0.md §3.2.1) ---

    @Test
    public void falseDisablesTheActionWildcardJustLikeNull() {
        PolicyDefinition.Meta meta = PolicyDefinition.Meta.builder().anyAction(false).build();

        Policy policy = Policy.from(new PolicyDefinition("1.0.0", null, null, meta, List.of(
            new PolicyDefinition.Rule("allow", "_ANY_", "Article", null)
        )));

        // With the wildcard disabled, "_ANY_" is just an ordinary, literal
        // action name - it does not match "Read".
        assertTrue(policy.cannot(ActionFactory.create("Read"), SubjectFactory.create("Article")));
        assertTrue(policy.can(ActionFactory.create("_ANY_"), SubjectFactory.create("Article")));
    }

    @Test
    public void throwsPolicyLoadExceptionWhenAnyActionIsDeclaredTrue() {
        assertThrows(PolicyLoadException.class, () ->
            PolicyDefinition.Meta.builder().anyAction(true));
    }

    @Test
    public void throwsPolicyLoadExceptionWhenAnyActionIsDeclaredANonBooleanNonStringValue() {
        assertThrows(PolicyLoadException.class, () ->
            PolicyDefinition.Meta.builder().anyAction(42));
    }

    // --- PolicyBuilder derives meta.actions/subjects/operators from usage; only the wildcard tokens are ever declared explicitly ---

    @Test
    public void buildDefDerivesActionsSubjectsAndOperatorsFromWhatWasActuallyUsed() {
        Subject<?> article = SubjectFactory.create("Article");
        Subject<?> user = SubjectFactory.create("User");
        Action<String> read = ActionFactory.create("Read");
        Action<String> update = ActionFactory.create("Update");
        Operator hasRole = Operator.of("$hasRole", (s, v, ctx) -> true);

        PolicyDefinition def = new PolicyBuilder(List.of(hasRole))
            .allow(read, article)
            .allow(update, user, Map.of("$hasRole", "admin"))
            .buildDef();

        assertEquals(List.of("Read", "Update"), def.getMeta().getActions());
        assertEquals(List.of("Article", "User"), def.getMeta().getSubjects());
        assertEquals(List.of("$hasRole"), def.getMeta().getOperators());
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
        assertEquals(null, def.getMeta().getAnyAction());
        assertEquals(null, def.getMeta().getAnySubject());
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
                .allow(ActionFactory.create("*"), SubjectFactory.create("*"), Map.of("owner_id", 1)));
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

        assertEquals(List.of("Read", "Delete"), def.getMeta().getActions());
        assertEquals(List.of("Article", "Comment"), def.getMeta().getSubjects());
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

        assertEquals(null, def.getMeta().getAnyAction());
        assertEquals(null, def.getMeta().getAnySubject());
    }
}
