package com.cptnfizzbin.examples.model;

import lombok.Data;

@Data
public class Article {
    private Long id;
    private Long ownerId;
    private String status;
}
