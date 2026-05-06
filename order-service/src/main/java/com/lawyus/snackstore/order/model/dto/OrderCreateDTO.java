package com.lawyus.snackstore.order.model.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.Setter;

import java.util.List;

@Getter
@Setter
public class OrderCreateDTO {

    @NotEmpty(message = "商品列表不能为空")
    private List<OrderItemDTO> items;

    @NotBlank(message = "收货人姓名不能为空")
    private String receiverName;

    @NotBlank(message = "收货人手机号不能为空")
    private String receiverPhone;

    @NotBlank(message = "收货地址不能为空")
    private String receiverAddress;

    @Getter
    @Setter
    public static class OrderItemDTO {

        @NotNull(message = "商品ID不能为空")
        private Long productId;

        @NotNull(message = "购买数量不能为空")
        private Integer quantity;
    }
}
