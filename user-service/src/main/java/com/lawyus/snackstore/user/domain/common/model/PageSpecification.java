package com.lawyus.snackstore.user.domain.common.model;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public record PageSpecification(int pageNum, int pageSize) {

    private static final Logger log = LoggerFactory.getLogger(PageSpecification.class);

    public PageSpecification {
        if (pageNum < 1) {
            log.warn("pageNum={} 非法，已重置为1", pageNum);
            pageNum = 1;
        }
        if (pageSize < 1 || pageSize > 100) {
            log.warn("pageSize={} 超出范围[1,100]，已重置为10", pageSize);
            pageSize = 10;
        }
    }

    public static PageSpecification of(int pageNum, int pageSize) {
        return new PageSpecification(pageNum, pageSize);
    }
}