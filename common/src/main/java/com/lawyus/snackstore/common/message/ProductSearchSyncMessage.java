package com.lawyus.snackstore.common.message;

import lombok.Getter;
import lombok.Setter;

import java.io.Serial;
import java.io.Serializable;
import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 商品搜索索引同步消息
 */
@Getter
@Setter
public class ProductSearchSyncMessage implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    private Long id;

    private Long categoryId;

    private String categoryName;

    private Integer categorySort;

    private String name;

    private String coverImage;

    private BigDecimal price;

    private Integer stock;

    private String description;

    private Integer status;

    private LocalDateTime createdAt;

    private ChangeType changeType;

    public enum ChangeType {
        CREATED,
        UPDATED,
        DELETED,
        STATUS_CHANGED,
        STOCK_CHANGED
    }
}
