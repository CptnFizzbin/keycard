package com.cptnfizzbin.keycard.examples.production;

import com.cptnfizzbin.keycard.KeycardConfig;
import com.cptnfizzbin.keycard.builder.PolicyBuilder;
import com.cptnfizzbin.keycard.conditions.Condition;
import com.cptnfizzbin.keycard.examples.production.model.Article;
import com.cptnfizzbin.keycard.examples.production.model.Comment;
import com.cptnfizzbin.keycard.examples.production.model.User;
import com.cptnfizzbin.keycard.policy.Policy;
import lombok.val;

public class AppPolicyBuilder extends PolicyBuilder {
    public static KeycardConfig config = new KeycardConfig()
        .actions(AppActions.getCatalog())
        .subjects(AppSubjects.getCatalog());

    public AppPolicyBuilder() {
        super(config);
    }

    public static Policy createUserPolicy(User user) {
        val builder = new PolicyBuilder();

        builder
            .allow(AppActions.CreateAndAccess, AppSubjects.Article)
            .allow(AppActions.Manage, AppSubjects.Comment, Condition.eq(Comment::getUserId, user.getId()))
            .deny(AppActions.Delete, AppSubjects.Article, Condition.eq(Article::getStatus, "published"))
        ;

        return builder.build();
    }
}
