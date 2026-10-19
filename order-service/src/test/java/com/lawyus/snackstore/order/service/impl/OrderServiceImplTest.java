package com.lawyus.snackstore.order.service.impl;

import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import com.lawyus.snackstore.common.response.Result;
import com.lawyus.snackstore.order.constant.OrderStatusConstants;
import com.lawyus.snackstore.order.exception.BusinessException;
import com.lawyus.snackstore.order.exception.BusinessExceptionEnum;
import com.lawyus.snackstore.order.feign.product.BatchStockDTO;
import com.lawyus.snackstore.order.feign.product.ProductClient;
import com.lawyus.snackstore.order.feign.product.ProductVO;
import com.lawyus.snackstore.order.mapper.OrderItemMapper;
import com.lawyus.snackstore.order.mapper.OrderMapper;
import com.lawyus.snackstore.order.model.dto.OrderCreateDTO;
import com.lawyus.snackstore.order.model.entity.Order;
import com.lawyus.snackstore.order.model.entity.OrderItem;
import org.apache.ibatis.builder.MapperBuilderAssistant;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InOrder;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowableOfType;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 下单与订单状态流转的特征化测试（回归防线）。
 *
 * <p>目的不是验证「新功能」，而是把当前已修复的关键语义钉死，使后续库存补偿改造
 * （docs/库存补偿方案.md 的 S1~S4）能在红灯时立刻暴露「改坏了业务规则」。
 * 全部为纯单元测试：Mockito 隔离 Mapper 与 Feign，不依赖 MySQL / Redis / Nacos。
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("OrderServiceImpl 下单与状态流转回归防线")
class OrderServiceImplTest {

    @Mock
    private OrderMapper orderMapper;

    @Mock
    private OrderItemMapper orderItemMapper;

    @Mock
    private ProductClient productClient;

    @InjectMocks
    private OrderServiceImpl orderService;

    /**
     * LambdaQueryWrapper / LambdaUpdateWrapper 将方法引用解析成列名依赖 MyBatis-Plus 的
     * TableInfo 缓存，而该缓存 normally 由 Mapper 注册时填充。纯单元测试不起 MyBatis 会话，
     * 不初始化会在 set()/eq() 处抛 "can not find lambda cache for this entity"。
     */
    @BeforeAll
    static void installLambdaColumnCache() {
        MapperBuilderAssistant assistant =
                new MapperBuilderAssistant(new MybatisConfiguration(), "");
        assistant.setCurrentNamespace(OrderMapper.class.getName());
        TableInfoHelper.initTableInfo(assistant, Order.class);
        TableInfoHelper.initTableInfo(assistant, OrderItem.class);
    }

    private static OrderCreateDTO createDto() {
        OrderCreateDTO dto = new OrderCreateDTO();
        dto.setReceiverName("张三");
        dto.setReceiverPhone("13800000000");
        dto.setReceiverAddress("某市某区某路 1 号");
        dto.setItems(List.of(item(1L, 2), item(2L, 3)));
        return dto;
    }

    private static OrderCreateDTO.OrderItemDTO item(Long productId, int quantity) {
        OrderCreateDTO.OrderItemDTO it = new OrderCreateDTO.OrderItemDTO();
        it.setProductId(productId);
        it.setQuantity(quantity);
        return it;
    }

    private static ProductVO product(Long id, String price, Integer status) {
        ProductVO vo = new ProductVO();
        vo.setId(id);
        vo.setName("零食-" + id);
        vo.setCoverImage("http://img/" + id);
        vo.setPrice(new BigDecimal(price));
        vo.setStock(100);
        vo.setStatus(status);
        return vo;
    }

    @Test
    @DisplayName("下单金额=Σ(单价×数量)，且订单先落库再远程扣库存")
    void createOrderComputesAmountAndInsertsOrderBeforeDeductingStock() {
        when(productClient.getProductsByIds(anyList()))
                .thenReturn(Result.success(List.of(product(1L, "12.50", 1), product(2L, "8.00", 1))));
        when(productClient.batchDeductStock(any(BatchStockDTO.class))).thenReturn(Result.success(true));

        orderService.createOrder(9L, createDto());

        ArgumentCaptor<Order> captor = ArgumentCaptor.forClass(Order.class);
        verify(orderMapper).insert(captor.capture());
        Order saved = captor.getValue();

        // 12.50×2 + 8.00×3 = 49.00
        assertThat(saved.getTotalAmount()).isEqualByComparingTo("49.00");
        assertThat(saved.getStatus()).isEqualTo(OrderStatusConstants.PENDING);
        assertThat(saved.getUserId()).isEqualTo(9L);
        assertThat(saved.getOrderNo()).isNotBlank();

        // H6 的核心修复：先插订单拿 id，再扣库存；顺序倒过来会让 orderItem 拿不到 orderId
        InOrder callOrder = inOrder(orderMapper, productClient);
        callOrder.verify(orderMapper).insert(any(Order.class));
        callOrder.verify(productClient).batchDeductStock(any(BatchStockDTO.class));
    }

    @Test
    @DisplayName("库存扣减返回 false 时抛 STOCK_NOT_ENOUGH（补偿改造前必须钉住）")
    void createOrderFailsWhenStockDeductionReturnsFalse() {
        when(productClient.getProductsByIds(anyList()))
                .thenReturn(Result.success(List.of(product(1L, "12.50", 1), product(2L, "8.00", 1))));
        when(productClient.batchDeductStock(any(BatchStockDTO.class))).thenReturn(Result.success(false));

        BusinessException ex = catchThrowableOfType(
                () -> orderService.createOrder(9L, createDto()), BusinessException.class);

        assertThat(ex).isNotNull();
        assertThat(ex.getCode()).isEqualTo(BusinessExceptionEnum.STOCK_NOT_ENOUGH.getCode());
    }

    @Test
    @DisplayName("商品 status 为 null 视为下架：抛 PRODUCT_OFF_SHELF 而非 NPE")
    void createOrderTreatsNullStatusAsOffShelf() {
        // 跨模块契约漂移的风险点：product-service 的 ProductVO 字段多于 order 侧副本，
        // 一旦 status 因反序列化缺失变成 null，这里必须走下架分支而不是解引用 NPE。
        when(productClient.getProductsByIds(anyList()))
                .thenReturn(Result.success(List.of(product(1L, "12.50", null), product(2L, "8.00", 1))));

        BusinessException ex = catchThrowableOfType(
                () -> orderService.createOrder(9L, createDto()), BusinessException.class);

        assertThat(ex).isNotNull();
        assertThat(ex.getCode()).isEqualTo(BusinessExceptionEnum.PRODUCT_OFF_SHELF.getCode());
        verify(productClient, never()).batchDeductStock(any());
    }

    @Test
    @DisplayName("返回商品数与下单数不一致时抛 ORDER_CREATE_FAILED")
    void createOrderFailsWhenProductCountMismatch() {
        when(productClient.getProductsByIds(anyList()))
                .thenReturn(Result.success(List.of(product(1L, "12.50", 1))));

        BusinessException ex = catchThrowableOfType(
                () -> orderService.createOrder(9L, createDto()), BusinessException.class);

        assertThat(ex).isNotNull();
        assertThat(ex.getCode()).isEqualTo(BusinessExceptionEnum.ORDER_CREATE_FAILED.getCode());
    }

    @Test
    @DisplayName("支付：条件更新影响 0 行且订单存在 → ORDER_CANNOT_PAY")
    void payOrderRejectsWhenConditionalUpdateMatchesNothing() {
        when(orderMapper.update(isNull(), any())).thenReturn(0);
        when(orderMapper.selectOne(any())).thenReturn(persistedOrder(OrderStatusConstants.COMPLETED));

        BusinessException ex = catchThrowableOfType(
                () -> orderService.payOrder(100L, 9L), BusinessException.class);

        assertThat(ex).isNotNull();
        assertThat(ex.getCode()).isEqualTo(BusinessExceptionEnum.ORDER_CANNOT_PAY.getCode());
    }

    @Test
    @DisplayName("支付：订单不存在 → ORDER_NOT_FOUND")
    void payOrderReportsNotFoundWhenOrderAbsent() {
        when(orderMapper.update(isNull(), any())).thenReturn(0);

        BusinessException ex = catchThrowableOfType(
                () -> orderService.payOrder(100L, 9L), BusinessException.class);

        assertThat(ex).isNotNull();
        assertThat(ex.getCode()).isEqualTo(BusinessExceptionEnum.ORDER_NOT_FOUND.getCode());
    }

    @Test
    @DisplayName("取消：库存回滚失败必须抛异常触发事务回滚，不能静默成功")
    void cancelOrderAbortsWhenStockRollbackFails() {
        when(orderMapper.selectOne(any())).thenReturn(persistedOrder(OrderStatusConstants.PENDING));
        when(orderMapper.update(isNull(), any())).thenReturn(1);
        when(productClient.batchRollbackStock(any(BatchStockDTO.class))).thenReturn(Result.success(false));

        BusinessException ex = catchThrowableOfType(
                () -> orderService.cancelOrder(100L, 9L), BusinessException.class);

        assertThat(ex).isNotNull();
        assertThat(ex.getCode()).isEqualTo(BusinessExceptionEnum.ORDER_CANCEL_FAILED.getCode());
    }

    @Test
    @DisplayName("删除：仅已取消订单可删，其他状态 → ORDER_STATUS_ERROR")
    void deleteOrderRejectsNonCancelledOrder() {
        when(orderMapper.delete(any())).thenReturn(0);
        when(orderMapper.selectOne(any())).thenReturn(persistedOrder(OrderStatusConstants.COMPLETED));

        BusinessException ex = catchThrowableOfType(
                () -> orderService.deleteOrder(100L, 9L), BusinessException.class);

        assertThat(ex).isNotNull();
        assertThat(ex.getCode()).isEqualTo(BusinessExceptionEnum.ORDER_STATUS_ERROR.getCode());
    }

    private static Order persistedOrder(int status) {
        Order order = new Order();
        order.setId(100L);
        order.setOrderNo("1234567890");
        order.setUserId(9L);
        order.setStatus(status);
        order.setTotalAmount(new BigDecimal("49.00"));
        return order;
    }
}
