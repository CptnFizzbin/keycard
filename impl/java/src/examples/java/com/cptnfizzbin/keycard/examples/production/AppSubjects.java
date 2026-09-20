package com.cptnfizzbin.keycard.examples.production;

import com.cptnfizzbin.keycard.examples.production.model.Article;
import com.cptnfizzbin.keycard.examples.production.model.Comment;
import com.cptnfizzbin.keycard.examples.production.model.User;
import com.cptnfizzbin.keycard.subject.Subject;
import com.cptnfizzbin.keycard.subject.SubjectCatalog;

public class AppSubjects {
    public static final SubjectCatalog catalog = new SubjectCatalog();

    public static Subject<User, ?> User = catalog.set("user", new Subject<>());
    public static Subject<Article, ?> Article = catalog.set("article", new Subject<>());
    public static Subject<Comment, ?> Comment = catalog.set("comment", new Subject<>());

    public static SubjectCatalog getCatalog() {
        return catalog;
    }
}
