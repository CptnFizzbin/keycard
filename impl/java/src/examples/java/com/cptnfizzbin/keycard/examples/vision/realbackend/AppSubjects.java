package com.cptnfizzbin.keycard.examples.vision.realbackend;

import com.cptnfizzbin.keycard.subject.Subject;
import com.cptnfizzbin.keycard.subject.SubjectCatalog;

public class AppSubjects {
    public static final SubjectCatalog catalog = new SubjectCatalog();

    public static ProjectSubject Project = catalog.set("project", new ProjectSubject());
    public static TaskSubject Task = catalog.set("task", new TaskSubject());
    // a plugin module can register its own dynamic Subject here too,
    // without touching anything that already shipped
    public static Subject<CommentSubjectClaims, ?> Comment = catalog.set("comment", new Subject<>());
}
