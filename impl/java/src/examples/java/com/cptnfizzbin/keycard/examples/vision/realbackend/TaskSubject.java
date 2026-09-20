package com.cptnfizzbin.keycard.examples.vision.realbackend;

import com.cptnfizzbin.keycard.subject.Subject;
import lombok.Data;
import lombok.experimental.Accessors;

import java.time.Instant;

/**
 * website/docs-java/vision-real-backend.md's {@code policy/TaskSubject.java}.
 */
public class TaskSubject extends Subject<TaskSubject.Claims, TaskSubject> {
    public TaskSubject() {
        super();
    }

    private TaskSubject(Subject<Claims, TaskSubject> prev, Claims instance) {
        super(prev, instance);
    }

    @Override
    protected TaskSubject copy(Claims instance) {
        return new TaskSubject(this, instance);
    }

    // composed from two entities - a Task alone doesn't carry orgId, but every
    // Condition that scopes access to an org needs it on the subject
    public TaskSubject from(Task t, Project p) {
        return wrap(new Claims()
            .orgId(p.getOrgId())
            .assigneeId(t.getAssigneeId())
            .createdAt(t.getCreatedAt()));
    }

    @Data
    @Accessors(fluent = true, chain = true)
    public static class Claims {
        private String orgId;
        private String assigneeId;
        private Instant createdAt;
    }
}
