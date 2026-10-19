package com.lawyus.snackstore.order.feign.product;

import com.lawyus.snackstore.common.response.Result;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.stereotype.Component;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * {@link ProductClientFallbackFactory} 的单元测试：商品服务不可用时的降级形状。
 *
 * <p>两个必须钉住的点：
 * <ul>
 *   <li><b>降级是失败关闭的</b>——所有方法返回 code=500 且 data 为空，绝不返回 200+空数据。
 *       下单链路靠 {@code code != 200} 判定失败并回滚，若降级返回成功形状，商品服务宕机时
 *       会「下单成功但库存没扣」，这正是库存黑洞的一种形态。</li>
 *   <li>工厂类必须带 {@code @Component}。Sentinel 的 SentinelFeign 按类型从容器里取
 *       fallbackFactory，取不到会在启动时直接失败（M/H 系列已修过一次，别退化）。</li>
 * </ul>
 */
@DisplayName("商品客户端降级工厂：失败关闭 + 必须注册为 Bean")
class ProductClientFallbackFactoryTest {

    private final ProductClient client = new ProductClientFallbackFactory().create(new RuntimeException("boom"));

    @Test
    @DisplayName("工厂必须标注 @Component，否则 SentinelFeign 启动即失败")
    void factoryMustBeARegisteredComponent() {
        assertThat(ProductClientFallbackFactory.class.isAnnotationPresent(Component.class)).isTrue();
        assertThat(client).isNotNull();
    }

    @Test
    @DisplayName("库存扣减与回滚降级返回 500，不得伪装成成功")
    void stockOperationsShouldFailClosed() {
        BatchStockDTO batch = new BatchStockDTO();

        Result<Boolean> deduct = client.deductStock(1L, 2);
        Result<Boolean> rollback = client.rollbackStock(1L, 2);
        Result<Boolean> batchDeduct = client.batchDeductStock(batch);
        Result<Boolean> batchRollback = client.batchRollbackStock(batch);

        for (Result<Boolean> result : List.of(deduct, rollback, batchDeduct, batchRollback)) {
            assertThat(result.getCode()).isEqualTo(500);
            assertThat(result.getData()).isNull();
            assertThat(result.getMessage()).isEqualTo("ProductService unavailable");
        }
    }

    @Test
    @DisplayName("查询类降级同样返回 500，让调用方走失败分支而不是空列表成功")
    void queriesShouldFailClosed() {
        assertThat(client.getProductDetail(1L).getCode()).isEqualTo(500);
        assertThat(client.getProductDetail(1L).getData()).isNull();

        Result<List<ProductVO>> listResult = client.getProductsByIds(List.of(1L, 2L));
        assertThat(listResult.getCode()).isEqualTo(500);
        assertThat(listResult.getData()).isNull();
    }
}
