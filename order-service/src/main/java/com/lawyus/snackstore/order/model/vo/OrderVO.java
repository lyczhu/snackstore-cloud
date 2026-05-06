package com.lawyus.snackstore.order.model.vo;

import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

@Getter
@Setter
public class OrderVO {

    private Long id;

    private String orderNo;

    private BigDecimal totalAmount;

    private Integer status;

    private String receiverName;

    private String receiverPhone;

    private String receiverAddress;

    private LocalDateTime payTime;

    private LocalDateTime createdAt;

    private List<OrderItemVO> items;

    @Getter
    @Setter
    public static class OrderItemVO {

        private Long id;

        private Long productId;

        private String productName;

        private String productImage;

        private BigDecimal productPrice;

        private Integer quantity;
    }
}
