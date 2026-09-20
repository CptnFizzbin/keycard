package com.cptnfizzbin.keycard.examples.vision;

import com.cptnfizzbin.keycard.errors.PolicyException;
import com.cptnfizzbin.keycard.examples.vision.quickstart.AppSubjects;
import com.cptnfizzbin.keycard.examples.vision.quickstart.Article;
import com.cptnfizzbin.keycard.examples.vision.quickstart.ArticleSubject;
import com.cptnfizzbin.keycard.examples.vision.quickstart.QuickstartDemo;
import com.cptnfizzbin.keycard.examples.vision.quickstart.User;
import com.cptnfizzbin.keycard.policy.Policy;
import org.junit.Test;

import java.util.List;

import static org.junit.Assert.*;

/**
 * Exercises website/docs-java/vision-quickstart.md's example end to end,
 * asserting the exact outcomes the doc calls out inline.
 */
public class QuickstartDemoTest {
    @Test
    public void matchesTheOutcomesTheDocCallsOutInline() {
        User user = new User();
        user.setId(1L);

        Policy policy = QuickstartDemo.policyForUser(user);

        Article draftArticle = new Article();
        draftArticle.setOwnerId(1L);
        draftArticle.setEditorIds(List.of());
        draftArticle.setStatus("draft");

        Article publishedArticle = new Article();
        publishedArticle.setOwnerId(1L);
        publishedArticle.setEditorIds(List.of());
        publishedArticle.setStatus("published");

        ArticleSubject draft = AppSubjects.Article.from(draftArticle);
        ArticleSubject published = AppSubjects.Article.from(publishedArticle);

        assertTrue(policy.can(QuickstartDemo.create, AppSubjects.Article)); // no conditions to satisfy
        assertTrue(policy.can(QuickstartDemo.delete, draft));                                                                  // owns it, still a draft
        assertFalse(policy.can(QuickstartDemo.delete, published));                                                             // deny rule matches last

        assertThrows(PolicyException.class, () -> policy.require(QuickstartDemo.delete, published));
    }

    @Test
    public void updateIsAllowedForTheOwnerOrAnEditor() {
        User owner = new User();
        owner.setId(1L);
        User editor = new User();
        editor.setId(2L);
        User stranger = new User();
        stranger.setId(3L);

        Article article = new Article();
        article.setOwnerId(1L);
        article.setEditorIds(List.of(2L));
        article.setStatus("draft");

        ArticleSubject subject = AppSubjects.Article.from(article);

        assertTrue(QuickstartDemo.policyForUser(owner).can(QuickstartDemo.update, subject));
        assertTrue(QuickstartDemo.policyForUser(editor).can(QuickstartDemo.update, subject));
        assertFalse(QuickstartDemo.policyForUser(stranger).can(QuickstartDemo.update, subject));
    }
}
