package com.lawyus.snackstore.common.dto;

import lombok.Getter;
import lombok.Setter;

import java.io.Serial;
import java.io.Serializable;
import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 商品搜索数据契约
 * <p>
 * product-service 内部接口与 product-search-service 之间的同步传输契约，
 * 同时也是 ES 索引构建的数据来源。仅承载商品快照字段，不包含任何事件元数据
 * （eventId/eventTime/changeType），事件传输由 {@code ProductSearchSyncMessage} 负责。
 */
@Getter
@Setter
public class ProductSearchItemDTO implements Serializable {

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
}
