package com.cptnfizzbin.keycard.examples.vision.realbackend;

import lombok.Data;

@Data
public class Project {
    private String id;
    private String orgId;
    private String ownerId;
    private java.time.Instant archivedAt;
}
