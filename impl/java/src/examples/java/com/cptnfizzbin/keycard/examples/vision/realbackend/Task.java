package com.cptnfizzbin.keycard.examples.vision.realbackend;

import lombok.Data;

import java.time.Instant;

@Data
public class Task {
    private String id;
    private String projectId;
    private String assigneeId;
    private Instant createdAt;
}
