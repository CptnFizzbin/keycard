package com.cptnfizzbin.keycard.examples.vision.quickstart;

import lombok.Data;

import java.util.List;

@Data
public class Article {
    private long ownerId;
    private List<Long> editorIds;
    private String status;
}
