package com.cptnfizzbin.keycard.examples.vision.quickstart;

import com.cptnfizzbin.keycard.action.Action;
import com.cptnfizzbin.keycard.builder.PolicyBuilder;
import com.cptnfizzbin.keycard.conditions.Condition;
import com.cptnfizzbin.keycard.policy.Policy;

/**
 * website/docs-java/vision-quickstart.md's {@code Main.java}.
 */
public class QuickstartDemo {
    public static final Action create = new Action("create");
    public static final Action update = new Action("update");
    public static final Action delete = new Action("delete");

    // Policy Claims: the only input this policy needs is who's asking
    // KeycardConfig is optional here - PolicyBuilder just needs the Actions/
    // Subjects used in .allow()/.deny() calls, not a full app-wide catalog
    public static Policy policyForUser(User user) {
        Condition<ArticleSubject.Claims> isOwner = Condition.eq(ArticleSubject.Claims::ownerId, user.getId());
        Condition<ArticleSubject.Claims> isEditor = Condition.has(ArticleSubject.Claims::editorIds, user.getId());
        Condition<ArticleSubject.Claims> isPublished = Condition.eq(ArticleSubject.Claims::status, "published");

        return new PolicyBuilder()
            .allow(create, AppSubjects.Article)
            .allow(update, AppSubjects.Article, Condition.where(Condition.or(isOwner, isEditor)))
            .allow(delete, AppSubjects.Article, Condition.where(isOwner))
            .deny(delete, AppSubjects.Article, Condition.where(isPublished)) // last match wins: locked once live
            .build();
    }
}
