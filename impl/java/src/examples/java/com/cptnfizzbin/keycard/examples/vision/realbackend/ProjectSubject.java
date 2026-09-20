package com.cptnfizzbin.keycard.examples.vision.realbackend;

import com.cptnfizzbin.keycard.subject.Subject;
import lombok.Data;
import lombok.experimental.Accessors;

/**
 * website/docs-java/vision-real-backend.md's {@code policy/ProjectSubject.java}.
 * <p>
 * Subject Types are recommended to be minimal focused subsets or computable
 * fields from one or more objects - not the full object.
 */
public class ProjectSubject extends Subject<ProjectSubject.Claims, ProjectSubject> {
    public ProjectSubject() {
        super();
    }

    private ProjectSubject(Subject<Claims, ProjectSubject> prev, Claims instance) {
        super(prev, instance);
    }

    @Override
    protected ProjectSubject copy(Claims instance) {
        return new ProjectSubject(this, instance);
    }

    public ProjectSubject from(Project p) {
        return wrap(new Claims()
            .orgId(p.getOrgId())
            .ownerId(p.getOwnerId())
            .archived(p.getArchivedAt() != null));
    }

    @Data
    @Accessors(fluent = true, chain = true)
    public static class Claims {
        private String orgId;
        private String ownerId;
        private boolean archived;
    }
}
