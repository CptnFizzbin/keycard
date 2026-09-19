package com.cptnfizzbin.examples;

import com.cptnfizzbin.examples.model.Article;
import com.cptnfizzbin.examples.model.Comment;
import com.cptnfizzbin.examples.model.User;
import com.cptnfizzbin.keycard.KeycardConfig;
import com.cptnfizzbin.keycard.action.Action;
import com.cptnfizzbin.keycard.builder.PolicyBuilder;
import com.cptnfizzbin.keycard.conditions.Conditions;
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
            .allow(AppActions.Manage, AppSubjects.Comment, Conditions.eq(Comment::getUserId, user.getId()))
            .deny(AppActions.Delete, AppSubjects.Article, Conditions.eq(Article::getStatus, "published"))
        ;

        return builder.build();
    }
}
