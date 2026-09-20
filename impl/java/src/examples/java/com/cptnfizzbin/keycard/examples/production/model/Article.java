package com.cptnfizzbin.keycard.examples.production.model;

import lombok.Data;

@Data
public class Article {
    private Long id;
    private Long ownerId;
    private String status;
}
