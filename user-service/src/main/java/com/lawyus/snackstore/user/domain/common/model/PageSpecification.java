package com.lawyus.snackstore.user.domain.common.model;

public record PageSpecification(int pageNum, int pageSize) {

    public PageSpecification {
        if (pageNum < 1) {
            pageNum = 1;
        }
        if (pageSize < 1 || pageSize > 100) {
            pageSize = 10;
        }
    }

    public static PageSpecification of(int pageNum, int pageSize) {
        return new PageSpecification(pageNum, pageSize);
    }
}