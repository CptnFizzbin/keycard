package com.cptnfizzbin.examples;

import com.cptnfizzbin.examples.model.Article;
import com.cptnfizzbin.examples.model.Comment;
import com.cptnfizzbin.examples.model.User;
import com.cptnfizzbin.keycard.subject.Subject;
import com.cptnfizzbin.keycard.subject.SubjectCatalog;

public class AppSubjects {
    public static Subject<User> User = new Subject<>();
    public static Subject<Article> Article = new Subject<>();
    public static Subject<Comment> Comment = new Subject<>();

    public static SubjectCatalog getCatalog() {
        return new SubjectCatalog()
            .add("user", User)
            .add("article", Article)
            .add("comment", Comment);
    }
}
