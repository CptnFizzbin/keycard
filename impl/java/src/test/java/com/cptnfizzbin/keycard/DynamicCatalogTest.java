package com.cptnfizzbin.keycard;

import com.cptnfizzbin.keycard.action.Action;
import com.cptnfizzbin.keycard.builder.PolicyBuilder;
import com.cptnfizzbin.keycard.errors.PolicyArgumentException;
import com.cptnfizzbin.keycard.errors.PolicyLoadException;
import com.cptnfizzbin.keycard.policy.Policy;
import com.cptnfizzbin.keycard.policy.PolicyDefinition;
import com.cptnfizzbin.keycard.subject.Subject;
import com.cptnfizzbin.keycard.version.KeyCardVersion;
import org.junit.Test;

import java.util.List;

import static org.junit.Assert.*;

/**
 * Optional Action/Subject naming resolved via a KeycardConfig catalog - see
 * "Dynamic (unnamed) Subjects/Actions and the Catalog" in
 * docs/guidelines/keycard-api.md. Mirrors the JS test suite's
 * actionFactory.test.ts/subjectFactory.test.ts and the "dynamic (no-name)
 * Action/Subject resolved via a KeycardConfig catalog" describe blocks in
 * builder/policyBuilder.test.ts and policy/policy.test.ts.
 */
public class DynamicCatalogTest {

    // --- Action.create()/new Action() (no-arg) ---

    @Test
    public void actionCreateWithANameBehavesAsBeforeNotDynamic() {
        Action action = new Action("Read");

        assertEquals("Read", action.name());
        assertFalse(action.dynamic());
    }

    @Test
    public void actionCreateWithNoNameGeneratesAUsableIdAndMarksItDynamic() {
        Action action = new Action();

        assertFalse(action.name().isEmpty());
        assertTrue(action.dynamic());
    }

    @Test
    public void eachNoArgActionCreateCallGeneratesADistinctId() {
        Action a = new Action();
        Action b = new Action();

        assertNotEquals(a.name(), b.name());
    }

    // --- Subject.create()/new Subject<>() (no-arg) ---

    @Test
    public void subjectCreateWithANameBehavesAsBeforeNotDynamic() {
        Subject<?, ?> subject = new Subject<>("Article");

        assertEquals("Article", subject.name());
        assertFalse(subject.dynamic());
    }

    @Test
    public void subjectCreateWithNoNameGeneratesAUsableIdAndMarksItDynamic() {
        Subject<?, ?> subject = new Subject<>();

        assertFalse(subject.name().isEmpty());
        assertTrue(subject.dynamic());
    }

    @Test
    public void eachNoArgSubjectCreateCallGeneratesADistinctId() {
        Subject<?, ?> a = new Subject<>();
        Subject<?, ?> b = new Subject<>();

        assertNotEquals(a.name(), b.name());
    }

    @Test
    public void subjectWrapPreservesTheGeneratedIdAndDynamicMarker() {
        Subject<Integer, ?> subject = new Subject<>();
        Subject<Integer, ?> wrapped = subject.wrap(1);

        assertEquals(subject.name(), wrapped.name());
        assertTrue(wrapped.dynamic());
        assertEquals(1, wrapped.claims().orElseThrow().intValue());
    }

    // --- PolicyBuilder: dynamic (no-name) Action/Subject resolved via a KeycardConfig catalog ---

    @Test
    public void aKeyedCatalogsKeyNotTheDynamicDefsRandomIdIsWhatGetsSerialized() {
        Action create = new Action();
        Subject<?, ?> article = new Subject<>();

        KeycardConfig config = new KeycardConfig();
        config.actions().add("create", create);
        config.subjects().add("article", article);

        PolicyDefinition def = new PolicyBuilder(config).allow(create, article).buildDef();

        assertEquals("create", def.rules().get(0).action());
        assertEquals("article", def.rules().get(0).subjectName());
        assertEquals(List.of("create"), def.meta().actions());
        assertEquals(List.of("article"), def.meta().subjects());
    }

    @Test
    public void aPlainListConfigStillWorksExactlyAsBeforeNoCatalogNoResolution() {

        KeycardConfig config = new KeycardConfig();
        config.actions().add(new Action("Delete"));
        config.subjects().add(new Subject<>("Comment"));

        PolicyDefinition def = new PolicyBuilder(config)
            .allow(new Action("Read"), new Subject<>("Article"))
            .buildDef();

        assertEquals(List.of("Read", "Delete"), def.meta().actions());
        assertEquals(List.of("Article", "Comment"), def.meta().subjects());
    }

    @Test
    public void allowThrowsPolicyArgumentExceptionForADynamicActionNeverRegisteredInTheCatalog() {
        Action create = new Action();
        Subject<?, ?> article = new Subject<>("Article");

        KeycardConfig config = new KeycardConfig();
        config.actions().add("update", new Action());

        assertThrows(PolicyArgumentException.class, () -> new PolicyBuilder(config).allow(create, article));
    }

    @Test
    public void allowThrowsPolicyArgumentExceptionForADynamicSubjectNeverRegisteredInTheCatalog() {
        Action read = new Action("Read");
        Subject<?, ?> article = new Subject<>();

        assertThrows(PolicyArgumentException.class, () -> new PolicyBuilder(new KeycardConfig()).allow(read, article));
    }

    @Test
    public void registeringTheSameDynamicActionUnderTwoDifferentCatalogKeysThrowsAtConstruction() {
        Action create = new Action();
        KeycardConfig config = new KeycardConfig();
        config.actions()
            .add("update", create)
            .add("submit", create);

        assertThrows(PolicyArgumentException.class, () -> new PolicyBuilder(config));
    }

    @Test
    public void anExplicitlyNamedActionSubjectInAKeyedCatalogIsStillResolvedToItsCatalogKey() {
        // "if using a catalog, defining the name is optional" - a catalog
        // key wins for any entry, named or not.
        Action create = new Action("Create");
        Subject<?, ?> article = new Subject<>("Article");

        KeycardConfig config = new KeycardConfig();
        config.actions().add("submit", create);
        config.subjects().add("post", article);

        PolicyDefinition def = new PolicyBuilder(config).allow(create, article).buildDef();

        assertEquals("submit", def.rules().get(0).action());
        assertEquals("post", def.rules().get(0).subjectName());
    }

    // --- Policy: dynamic (no-name) Action/Subject resolved via a KeycardConfig catalog ---

    @Test
    public void aDynamicDefResolvesViaItsCatalogKeyToMatchARuleWrittenAgainstThatKey() {
        Action create = new Action();
        Subject<?, ?> article = new Subject<>();

        KeycardConfig config = new KeycardConfig();
        config.actions().add("create", create);
        config.subjects().add("article", article);

        Policy policy = new Policy(
            new PolicyDefinition()
                .version(KeyCardVersion.KEYCARD_POLICY_VERSION.toString())
                .rules(List.of(
                    new PolicyDefinition.Rule("allow", "create", "article", null)
                )),
            config
        );

        assertTrue(policy.can(create, article));
    }

    // Spec: https://keycard.cptnfizzbin.dev/spec/v0#metaactions--metasubjects
    @Test
    public void catalogCoverageIsStillEnforcedUsingCatalogResolvedNames() {
        KeycardConfig config = new KeycardConfig();
        config.actions().add("read", new Action());

        assertThrows(PolicyLoadException.class, () ->
            new Policy(new PolicyDefinition()
                .rules(List.of(
                    new PolicyDefinition.Rule("allow", "write", "article")
                )), config));
    }

    @Test
    public void omittingConfigLoggerFallsBackToTheNoOpLoggerWithoutThrowing() {
        Action ghost = new Action();
        Policy policy = new Policy(new PolicyDefinition());

        policy.can(ghost, new Subject<>("Article")); // should not throw
    }
}
