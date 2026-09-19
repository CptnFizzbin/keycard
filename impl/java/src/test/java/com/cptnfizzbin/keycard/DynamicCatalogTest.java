package com.cptnfizzbin.keycard;

import com.cptnfizzbin.keycard.action.Action;
import com.cptnfizzbin.keycard.action.ActionFactory;
import com.cptnfizzbin.keycard.builder.PolicyBuilder;
import com.cptnfizzbin.keycard.errors.PolicyArgumentException;
import com.cptnfizzbin.keycard.errors.PolicyLoadException;
import com.cptnfizzbin.keycard.lib.Logger;
import com.cptnfizzbin.keycard.policy.Policy;
import com.cptnfizzbin.keycard.policy.PolicyDefinition;
import com.cptnfizzbin.keycard.policy.WildcardToken;
import com.cptnfizzbin.keycard.subject.Subject;
import com.cptnfizzbin.keycard.subject.SubjectFactory;
import com.cptnfizzbin.keycard.version.KeyCardVersion;
import org.junit.Test;

import java.util.ArrayList;
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

    // --- Action.create()/ActionFactory.create() (no-arg) ---

    @Test
    public void actionCreateWithANameBehavesAsBeforeNotDynamic() {
        Action action = ActionFactory.create("Read");

        assertEquals("Read", action.getNameStr());
        assertFalse(action.dynamic());
    }

    @Test
    public void actionCreateWithNoNameGeneratesAUsableIdAndMarksItDynamic() {
        Action action = ActionFactory.create();

        assertTrue(action.getNameStr().length() > 0);
        assertTrue(action.dynamic());
    }

    @Test
    public void eachNoArgActionCreateCallGeneratesADistinctId() {
        Action a = ActionFactory.create();
        Action b = ActionFactory.create();

        assertNotEquals(a.getNameStr(), b.getNameStr());
    }

    // --- Subject.create()/SubjectFactory.create() (no-arg) ---

    @Test
    public void subjectCreateWithANameBehavesAsBeforeNotDynamic() {
        Subject<?> subject = SubjectFactory.create("Article");

        assertEquals("Article", subject.getName());
        assertFalse(subject.isDynamic());
    }

    @Test
    public void subjectCreateWithNoNameGeneratesAUsableIdAndMarksItDynamic() {
        Subject<?> subject = SubjectFactory.create();

        assertTrue(subject.getName().length() > 0);
        assertTrue(subject.isDynamic());
    }

    @Test
    public void eachNoArgSubjectCreateCallGeneratesADistinctId() {
        Subject<?> a = SubjectFactory.create();
        Subject<?> b = SubjectFactory.create();

        assertNotEquals(a.getName(), b.getName());
    }

    @Test
    public void subjectWrapPreservesTheGeneratedIdAndDynamicMarker() {
        Subject<Integer> subject = SubjectFactory.create();
        Subject<Integer> wrapped = subject.wrap(1);

        assertEquals(subject.getName(), wrapped.getName());
        assertTrue(wrapped.isDynamic());
        assertEquals(1, wrapped.getInstance().get().intValue());
    }

    // --- PolicyBuilder: dynamic (no-name) Action/Subject resolved via a KeycardConfig catalog ---

    @Test
    public void aKeyedCatalogsKeyNotTheDynamicDefsRandomIdIsWhatGetsSerialized() {
        Action create = ActionFactory.create();
        Subject<?> article = SubjectFactory.create();

        KeycardConfig config = KeycardConfig.builder()
            .addAction("create", create)
            .addSubject("article", article)
            .build();

        PolicyDefinition def = new PolicyBuilder(config).allow(create, article).buildDef();

        assertEquals("create", def.getRules().get(0).action());
        assertEquals("article", def.getRules().get(0).subjectName());
        assertEquals(List.of("create"), def.meta().actions());
        assertEquals(List.of("article"), def.meta().subjects());
    }

    @Test
    public void aPlainListConfigStillWorksExactlyAsBeforeNoCatalogNoResolution() {
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
    public void allowThrowsPolicyArgumentExceptionForADynamicActionNeverRegisteredInTheCatalog() {
        Action create = ActionFactory.create();
        Subject<?> article = SubjectFactory.create("Article");
        KeycardConfig config = KeycardConfig.builder()
            .addAction("update", ActionFactory.create())
            .build();

        assertThrows(PolicyArgumentException.class, () -> new PolicyBuilder(config).allow(create, article));
    }

    @Test
    public void allowThrowsPolicyArgumentExceptionForADynamicSubjectNeverRegisteredInTheCatalog() {
        Action read = ActionFactory.create("Read");
        Subject<?> article = SubjectFactory.create();

        assertThrows(PolicyArgumentException.class, () -> new PolicyBuilder(KeycardConfig.builder().build()).allow(read, article));
    }

    @Test
    public void registeringTheSameDynamicActionUnderTwoDifferentCatalogKeysThrowsAtConstruction() {
        Action create = ActionFactory.create();
        KeycardConfig config = KeycardConfig.builder()
            .addAction("create", create)
            .addAction("submit", create)
            .build();

        assertThrows(PolicyArgumentException.class, () -> new PolicyBuilder(config));
    }

    @Test
    public void anExplicitlyNamedActionSubjectInAKeyedCatalogIsStillResolvedToItsCatalogKey() {
        // "if using a catalog, defining the name is optional" - a catalog
        // key wins for any entry, named or not.
        Action create = ActionFactory.create("Create");
        Subject<?> article = SubjectFactory.create("Article");

        KeycardConfig config = KeycardConfig.builder()
            .addAction("submit", create)
            .addSubject("post", article)
            .build();

        PolicyDefinition def = new PolicyBuilder(config).allow(create, article).buildDef();

        assertEquals("submit", def.getRules().get(0).action());
        assertEquals("post", def.getRules().get(0).subjectName());
    }

    // --- Policy: dynamic (no-name) Action/Subject resolved via a KeycardConfig catalog ---

    @Test
    public void aDynamicDefResolvesViaItsCatalogKeyToMatchARuleWrittenAgainstThatKey() {
        Action create = ActionFactory.create();
        Subject<?> article = SubjectFactory.create();

        KeycardConfig config = KeycardConfig.builder()
            .addAction("create", create)
            .addSubject("article", article)
            .build();

        Policy policy = Policy.from(
            new PolicyDefinition()
                .version(KeyCardVersion.KEYCARD_POLICY_VERSION.toString())
                .rules(List.of(
                    new PolicyDefinition.Rule("allow", "create", "article", null)
                )),
            config
        );

        assertTrue(policy.can(create, article));
    }

    @Test
    public void ec8CoverageIsStillEnforcedUsingCatalogResolvedNames() {
        KeycardConfig config = KeycardConfig.builder()
            .addAction("read", ActionFactory.create())
            .build();

        assertThrows(PolicyLoadException.class, () ->
            Policy.from(new PolicyDefinition()
                .rules(List.of(
                    new PolicyDefinition.Rule("allow", "write", "article")
                )), config));
    }

    @Test
    public void anUnregisteredDynamicActionPassedToCanWarnsOnceDedupedViaConfigLoggerAndDenies() {
        List<String> warnings = new ArrayList<>();
        Logger logger = warnings::add;
        Action ghost = ActionFactory.create();
        Subject<?> article = SubjectFactory.create("Article");

        KeycardConfig config = KeycardConfig.builder().logger(logger).build();
        Policy policy = Policy.from(new PolicyDefinition()
            .rules(List.of(
                new PolicyDefinition.Rule("allow", "Read", "Article")
            )), config);

        assertFalse(policy.can(ghost, article));
        assertFalse(policy.can(ghost, article));
        assertEquals(1, warnings.size());
    }

    @Test
    public void anUnregisteredDynamicDefStillFallsThroughToAMatchingWildcardRule() {
        List<String> warnings = new ArrayList<>();
        Logger logger = warnings::add;
        Action ghost = ActionFactory.create();

        PolicyDefinition.Meta meta = new PolicyDefinition.Meta().anyAction(WildcardToken.of("_ANY_"));
        KeycardConfig config = KeycardConfig.builder().logger(logger).build();
        Policy policy = Policy.from(new PolicyDefinition()
            .meta(meta)
            .rules(List.of(
                new PolicyDefinition.Rule("allow", "_ANY_", "Article", null)
            )), config);

        assertTrue(policy.can(ghost, SubjectFactory.create("Article")));
    }

    @Test
    public void omittingConfigLoggerFallsBackToTheNoOpLoggerWithoutThrowing() {
        Action ghost = ActionFactory.create();
        Policy policy = Policy.from(new PolicyDefinition());

        policy.can(ghost, SubjectFactory.create("Article")); // should not throw
    }
}
