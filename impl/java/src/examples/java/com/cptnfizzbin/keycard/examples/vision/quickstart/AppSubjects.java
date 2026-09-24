package com.cptnfizzbin.keycard.examples.vision.quickstart;

import com.cptnfizzbin.keycard.subject.SubjectCatalog;

public class AppSubjects {
    public static final SubjectCatalog catalog = new SubjectCatalog();

    // ArticleSubject already carries its own name - set(subject) reads it
    // off the object itself, no separate name argument needed
    public static ArticleSubject Article = catalog.set(new ArticleSubject("article"));
}
