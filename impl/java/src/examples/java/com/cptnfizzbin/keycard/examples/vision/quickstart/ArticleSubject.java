package com.cptnfizzbin.keycard.examples.vision.quickstart;

import com.cptnfizzbin.keycard.subject.Subject;
import lombok.Data;
import lombok.experimental.Accessors;

import java.util.List;

/**
 * website/docs-java/vision-quickstart.md's {@code ArticleSubject.java} -
 * a dedicated Subject subclass, scoped to just the Claims its Conditions
 * need rather than the whole {@link Article}. {@code copy()} calls its own
 * private constructor, so {@link #from} returns {@code ArticleSubject}
 * itself with no cast anywhere - see {@link Subject}'s class doc.
 */
public final class ArticleSubject extends Subject<ArticleSubject.Claims, ArticleSubject> {
    public ArticleSubject(String name) {
        super(name);
    }

    private ArticleSubject(Subject<Claims, ArticleSubject> prev, Claims instance) {
        super(prev, instance);
    }

    @Override
    protected ArticleSubject copy(Claims instance) {
        return new ArticleSubject(this, instance);
    }

    public ArticleSubject from(Article a) {
        return wrap(new Claims()
            .ownerId(a.getOwnerId())
            .editorIds(a.getEditorIds())
            .status(a.getStatus()));
    }

    @Data
    @Accessors(fluent = true, chain = true)
    public static class Claims {
        private long ownerId;
        private List<Long> editorIds;
        private String status;
    }
}
