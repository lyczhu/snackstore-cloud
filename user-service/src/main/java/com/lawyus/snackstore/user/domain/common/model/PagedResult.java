package com.lawyus.snackstore.user.domain.common.model;

import java.util.List;

public record PagedResult<T>(List<T> content, long total, int pageNum, int pageSize) {

    public long getPages() {
        return (total + pageSize - 1) / pageSize;
    }
}