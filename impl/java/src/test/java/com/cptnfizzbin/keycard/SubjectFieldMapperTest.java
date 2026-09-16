package com.cptnfizzbin.keycard;

import lombok.AllArgsConstructor;
import lombok.Getter;
import org.junit.Test;

import com.cptnfizzbin.keycard.action.Action;
import com.cptnfizzbin.keycard.action.ActionFactory;
import com.cptnfizzbin.keycard.errors.PolicyLoadException;
import com.cptnfizzbin.keycard.policy.Policy;
import com.cptnfizzbin.keycard.policy.PolicyDefinition;
import com.cptnfizzbin.keycard.subject.Subject;
import com.cptnfizzbin.keycard.subject.SubjectFactory;
import com.cptnfizzbin.keycard.subject.SubjectFieldMapper;
import com.cptnfizzbin.keycard.subject.SubjectFieldMapperCatalog;

import java.util.List;
import java.util.Map;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;

public class SubjectFieldMapperTest {
    @Getter
    @AllArgsConstructor
    static class Author {
        private final String name;
    }

    @Getter
    @AllArgsConstructor
    static class Post {
        private final String status;
        private final Author author;
    }

    private static SubjectFieldMapper<Post> authorNameMapper() {
        return SubjectFieldMapper.<Post>builder()
            .field("authorName", (post) -> post.getAuthor().getName())
            .build();
    }

    @Test
    public void mappedFieldIsResolvedThroughItsGetterInsteadOfReflection() {
        Subject<Post> post = SubjectFactory.create("Post", authorNameMapper());
        Action<String> read = ActionFactory.create("Read");

        Policy policy = Policy.from(new PolicyDefinition("1.0.0", List.of(
            new PolicyDefinition.Rule("allow", "Read", "Post", Map.of("authorName", "Alice"))
        )));

        assertTrue(policy.can(read, post.wrap(new Post("draft", new Author("Alice")))));
        assertFalse(policy.can(read, post.wrap(new Post("draft", new Author("Bob")))));
    }

    @Test
    public void aFieldTheMapperDoesntDefineFallsBackToReflection() {
        Subject<Post> post = SubjectFactory.create("Post", authorNameMapper());
        Action<String> read = ActionFactory.create("Read");

        Policy policy = Policy.from(new PolicyDefinition("1.0.0", List.of(
            new PolicyDefinition.Rule("allow", "Read", "Post", Map.of("status", "draft"))
        )));

        assertTrue(policy.can(read, post.wrap(new Post("draft", new Author("Alice")))));
        assertFalse(policy.can(read, post.wrap(new Post("published", new Author("Alice")))));
    }

    @Test
    public void doesNotApplyOnceAFieldLookupHasNarrowedIntoANestedValue() {
        // The mapper is bound to the top-level Post instance - `author.name`
        // still resolves via reflection on the narrowed `author` object,
        // not through the mapper (which is never even asked about "name").
        Subject<Post> post = SubjectFactory.create("Post", authorNameMapper());
        Action<String> read = ActionFactory.create("Read");

        Policy policy = Policy.from(new PolicyDefinition("1.0.0", List.of(
            new PolicyDefinition.Rule("allow", "Read", "Post", Map.of("author", Map.of("name", "Alice")))
        )));

        assertTrue(policy.can(read, post.wrap(new Post("draft", new Author("Alice")))));
    }

    @Test
    public void stillAppliesInsideAndOrNotWhichEvaluateAgainstTheSameTopLevelSubject() {
        Subject<Post> post = SubjectFactory.create("Post", authorNameMapper());
        Action<String> read = ActionFactory.create("Read");

        Policy policy = Policy.from(new PolicyDefinition("1.0.0", List.of(
            new PolicyDefinition.Rule("allow", "Read", "Post", Map.of(
                "$and", List.of(
                    Map.of("authorName", "Alice"),
                    Map.of("$or", List.of(Map.of("status", "draft"), Map.of("authorName", "Alice")))
                )
            ))
        )));

        assertTrue(policy.can(read, post.wrap(new Post("published", new Author("Alice")))));
        assertFalse(policy.can(read, post.wrap(new Post("published", new Author("Bob")))));
    }

    @Test
    public void wrapCarriesTheFieldMapperForwardUnchanged() {
        Subject<Post> post = SubjectFactory.create("Post", authorNameMapper());
        Subject<Post> wrapped = post.wrap(new Post("draft", new Author("Alice")));

        assertEquals(post.getFieldMapper(), wrapped.getFieldMapper());
    }

    @Test
    public void catalogMapperIsConsultedWhenTheSubjectInHandCarriesNoneOfItsOwn() {
        Subject<Post> post = SubjectFactory.create("Post");
        Action<String> read = ActionFactory.create("Read");

        SubjectFieldMapperCatalog catalog = SubjectFieldMapperCatalog.builder()
            .register("Post", authorNameMapper())
            .build();
        KeycardConfig config = KeycardConfig.builder().mapper(catalog).build();

        Policy policy = Policy.from(new PolicyDefinition("1.0.0", List.of(
            new PolicyDefinition.Rule("allow", "Read", "Post", Map.of("authorName", "Alice"))
        )), config);

        assertTrue(policy.can(read, post.wrap(new Post("draft", new Author("Alice")))));
        assertFalse(policy.can(read, post.wrap(new Post("draft", new Author("Bob")))));
    }

    @Test
    public void aFieldMapperOnTheSubjectItselfTakesPrecedenceOverTheCatalog() {
        Subject<Post> post = SubjectFactory.create("Post", authorNameMapper());
        Action<String> read = ActionFactory.create("Read");

        // Deliberately mismatched, so the test can tell which one won.
        SubjectFieldMapperCatalog catalog = SubjectFieldMapperCatalog.builder()
            .register("Post", SubjectFieldMapper.<Post>builder().field("authorName", (p) -> "Mismatched").build())
            .build();
        KeycardConfig config = KeycardConfig.builder().mapper(catalog).build();

        Policy policy = Policy.from(new PolicyDefinition("1.0.0", List.of(
            new PolicyDefinition.Rule("allow", "Read", "Post", Map.of("authorName", "Alice"))
        )), config);

        assertTrue(policy.can(read, post.wrap(new Post("draft", new Author("Alice")))));
    }

    @Test
    public void configActionsAndSubjectsWidenTheEc8Catalog() {
        Subject<Post> post = SubjectFactory.create("Post");
        KeycardConfig config = KeycardConfig.builder()
            .actions(List.of(ActionFactory.create("Read")))
            .subjects(List.of(post))
            .build();

        assertThrows(PolicyLoadException.class, () ->
            Policy.from(new PolicyDefinition("1.0.0", List.of(
                new PolicyDefinition.Rule("allow", "Write", "Post", null)
            )), config));

        Policy.from(new PolicyDefinition("1.0.0", List.of(
            new PolicyDefinition.Rule("allow", "Read", "Post", null)
        )), config); // should not throw
    }
}
