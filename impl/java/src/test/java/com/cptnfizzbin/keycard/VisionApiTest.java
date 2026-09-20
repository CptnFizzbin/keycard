package com.cptnfizzbin.keycard;

import com.cptnfizzbin.keycard.action.Action;
import com.cptnfizzbin.keycard.action.ActionCatalog;
import com.cptnfizzbin.keycard.builder.PolicyBuilder;
import com.cptnfizzbin.keycard.conditions.Condition;
import com.cptnfizzbin.keycard.conditions.ConditionOperator;
import com.cptnfizzbin.keycard.conditions.OperatorCatalog;
import com.cptnfizzbin.keycard.errors.PolicyArgumentException;
import com.cptnfizzbin.keycard.policy.Policy;
import com.cptnfizzbin.keycard.policy.PolicyDefinition;
import com.cptnfizzbin.keycard.subject.Subject;
import com.cptnfizzbin.keycard.subject.SubjectCatalog;
import lombok.AllArgsConstructor;
import lombok.Getter;
import org.junit.Test;

import java.util.List;

import static org.junit.Assert.*;

/**
 * Covers the API additions made to align the Java implementation with
 * website/docs-java/vision-quickstart.md and vision-real-backend.md:
 * {@code ActionCatalog}/{@code SubjectCatalog#set}, the self-bounded
 * {@code Subject<T, TSelf>} subclassing hook, {@code Condition.field}/
 * {@code where}, {@link ConditionOperator}, and {@code KeycardConfig#emitMeta}.
 */
public class VisionApiTest {
    // --- ActionCatalog.set / SubjectCatalog.set ---

    @Test
    public void actionCatalogSetRegistersAndReturnsTheAction() {
        ActionCatalog catalog = new ActionCatalog();
        Action create = catalog.set("create", new Action());

        assertTrue(create.dynamic());
        assertSame(create, catalog.get("create"));
    }

    @Test
    public void actionCatalogSetWithNoKeyReadsTheActionsOwnName() {
        ActionCatalog catalog = new ActionCatalog();
        Action read = catalog.set(new Action("read"));

        assertSame(read, catalog.get("read"));
    }

    @Test
    public void actionCatalogSetWithNoKeyRejectsADynamicAction() {
        ActionCatalog catalog = new ActionCatalog();
        assertThrows(PolicyArgumentException.class, () -> catalog.set(new Action()));
    }

    @Test
    public void subjectCatalogSetRegistersAndReturnsTheSubjectsRealSubtype() {
        SubjectCatalog catalog = new SubjectCatalog();
        ArticleSubject article = catalog.set("article", new ArticleSubject());

        // set() returns ArticleSubject itself, not a plain Subject<?, ?> -
        // no cast needed to keep using it as an ArticleSubject.
        assertSame(article, catalog.get("article"));
        ArticleSubject wrapped = article.from("owner-1");
        assertEquals("owner-1", wrapped.claims().orElseThrow());
    }

    @Test
    public void subjectCatalogSetWithNoKeyReadsTheSubjectsOwnName() {
        SubjectCatalog catalog = new SubjectCatalog();
        Subject<?, ?> comment = catalog.set(new Subject<>("comment"));

        assertSame(comment, catalog.get("comment"));
    }

    @Test
    public void subjectCatalogSetWithNoKeyRejectsADynamicSubject() {
        SubjectCatalog catalog = new SubjectCatalog();
        assertThrows(PolicyArgumentException.class, () -> catalog.set(new Subject<>()));
    }

    // --- Subject<T, TSelf>: a dedicated subclass's wrap()/from() return its own subtype, no cast ---

    private static class ArticleSubject extends Subject<String, ArticleSubject> {
        ArticleSubject() {
            super();
        }

        private ArticleSubject(Subject<String, ArticleSubject> prev, String instance) {
            super(prev, instance);
        }

        @Override
        protected ArticleSubject copy(String instance) {
            return new ArticleSubject(this, instance);
        }

        ArticleSubject from(String ownerId) {
            return wrap(ownerId);
        }
    }

    @Test
    public void aSubjectSubclassesWrapReturnsItsOwnSubtypeWithNoCast() {
        ArticleSubject article = new ArticleSubject();
        ArticleSubject wrapped = article.from("owner-1");

        assertEquals(article.name(), wrapped.name());
        assertEquals("owner-1", wrapped.claims().orElseThrow());
    }

    // --- Condition.field/where ---

    @Getter
    @AllArgsConstructor
    static class Article {
        private final int ownerId;
        private final List<String> tags;
        private final String status;
    }

    @Test
    public void conditionFieldIsEquivalentToTheStaticHelperItWraps() {
        Subject<Article, ?> subject = new Subject<>("Article");
        Action update = new Action("Update");

        Policy policy = new PolicyBuilder()
            .allow(update, subject, Condition.<Article, Integer>field(Article::getOwnerId).eq(1))
            .build();

        assertTrue(policy.can(update, subject.wrap(new Article(1, List.of(), "draft"))));
        assertFalse(policy.can(update, subject.wrap(new Article(2, List.of(), "draft"))));
    }

    @Test
    public void conditionWhereIsAnIdentityPassthrough() {
        Condition<Article> condition = Condition.eq(Article::getOwnerId, 1);
        assertSame(condition, Condition.where(condition));
    }

    @Test
    public void conditionFieldHasSupportsCollectionFields() {
        Subject<Article, ?> subject = new Subject<>("Article");
        Action update = new Action("Update");

        Policy policy = new PolicyBuilder()
            .allow(update, subject, Condition.<Article, List<String>>field(Article::getTags).has("featured"))
            .build();

        assertTrue(policy.can(update, subject.wrap(new Article(1, List.of("featured"), "draft"))));
        assertFalse(policy.can(update, subject.wrap(new Article(1, List.of("news"), "draft"))));
    }

    // --- OperatorCatalog.set / ConditionOperator ---

    @Test
    public void operatorCatalogSetRegistersAFlatTwoArgOperatorAndReturnsIt() {
        OperatorCatalog catalog = new OperatorCatalog();
        ConditionOperator startsWith = catalog.set("$startsWith", (subjectValue, value) ->
            String.valueOf(subjectValue).startsWith(String.valueOf(value)));

        assertNotNull(startsWith);
        assertTrue(catalog.customNames().contains("$startsWith"));

        Subject<Article, ?> subject = new Subject<>("Article");
        Action read = new Action("Read");

        KeycardConfig config = new KeycardConfig().operators(catalog);
        Policy policy = new PolicyBuilder(config)
            .allow(read, subject, Condition.<Article, String>field(Article::getStatus).op("$startsWith", "dra"))
            .build();

        assertTrue(policy.can(read, subject.wrap(new Article(1, List.of(), "draft"))));
        assertFalse(policy.can(read, subject.wrap(new Article(1, List.of(), "published"))));
    }

    // --- KeycardConfig.emitMeta ---

    @Test
    public void emitMetaDefaultsToTrueAndAttachesMeta() {
        PolicyDefinition def = new PolicyBuilder()
            .allow(new Action("Read"), new Subject<>("Article"))
            .buildDef();

        assertNotNull(def.meta());
    }

    @Test
    public void emitMetaFalseOmitsMetaFromTheBuiltDefinition() {
        KeycardConfig config = new KeycardConfig().emitMeta(false);

        PolicyDefinition def = new PolicyBuilder(config)
            .allow(new Action("Read"), new Subject<>("Article"))
            .buildDef();

        assertNull(def.meta());
    }

    @Test
    public void emitMetaFalseSkipsTheEagerDynamicCatalogCheck() {
        KeycardConfig config = new KeycardConfig().emitMeta(false);

        // A dynamic Action with no catalog registration would normally
        // throw at addRule() time - emitMeta(false) skips that fail-fast
        // check, matching "a definition that already passed CI doesn't
        // need to re-prove itself on every boot".
        new PolicyBuilder(config).allow(new Action(), new Subject<>("Article")); // should not throw
    }

    @Test
    public void emitMetaFalseSkipsPolicysConstructionTimeValidation() {
        KeycardConfig config = new KeycardConfig().emitMeta(false);
        PolicyDefinition.Meta meta = new PolicyDefinition.Meta().operators(List.of("$neverRegistered"));

        // Normally throws PolicyLoadException because nothing registers
        // $neverRegistered - skipped when emitMeta is false.
        new Policy(new PolicyDefinition().meta(meta), config); // should not throw
    }

    @Test
    public void emitMetaTrueStillEnforcesConstructionTimeValidation() {
        PolicyDefinition.Meta meta = new PolicyDefinition.Meta().operators(List.of("$neverRegistered"));

        assertThrows(
            com.cptnfizzbin.keycard.errors.PolicyLoadException.class,
            () -> new Policy(new PolicyDefinition().meta(meta), new KeycardConfig())
        );
    }
}
