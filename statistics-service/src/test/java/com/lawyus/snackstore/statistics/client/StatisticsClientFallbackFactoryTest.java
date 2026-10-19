package com.lawyus.snackstore.statistics.client;

import com.lawyus.snackstore.common.response.Result;
import com.lawyus.snackstore.statistics.client.order.OrderClient;
import com.lawyus.snackstore.statistics.client.order.OrderClientFallbackFactory;
import com.lawyus.snackstore.statistics.client.product.ProductClient;
import com.lawyus.snackstore.statistics.client.product.ProductClientFallbackFactory;
import com.lawyus.snackstore.statistics.client.user.UserClient;
import com.lawyus.snackstore.statistics.client.user.UserClientFallbackFactory;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.stereotype.Component;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 统计服务三个 Feign 降级工厂的单元测试。
 *
 * <p>统计面板是纯读链路，所以这里的降级刻意<b>失败关闭</b>（返回 500）而不是返回 0 或空列表：
 * {@code StatisticsServiceImpl.extractData} 见到非 200 会抛异常，从而<b>不会把「全 0」写进 Redis</b>。
 * 如果哪天有人把降级改成返回空数据，面板会在上游抖动后的一整个 TTL 里显示假数据 —— 这组断言就是拦这个的。
 *
 * <p>同时钉住三个工厂都带 {@code @Component}（缺失会导致 SentinelFeign 启动即失败）。
 */
@DisplayName("统计客户端降级工厂：失败关闭 + 必须注册为 Bean")
class StatisticsClientFallbackFactoryTest {

    @Test
    @DisplayName("三个工厂都要注册为 Bean，且都能在没有下游时产出降级客户端")
    void allFactoriesMustBeComponents() {
        assertThat(OrderClientFallbackFactory.class.isAnnotationPresent(Component.class)).isTrue();
        assertThat(ProductClientFallbackFactory.class.isAnnotationPresent(Component.class)).isTrue();
        assertThat(UserClientFallbackFactory.class.isAnnotationPresent(Component.class)).isTrue();

        assertThat(new OrderClientFallbackFactory().create(new RuntimeException("boom"))).isNotNull();
        assertThat(new ProductClientFallbackFactory().create(new RuntimeException("boom"))).isNotNull();
        assertThat(new UserClientFallbackFactory().create(new RuntimeException("boom"))).isNotNull();
    }

    @Test
    @DisplayName("订单侧降级：统计/趋势/畅销全部返回 500 且数据为空")
    void orderClientShouldFailClosed() {
        OrderClient client = new OrderClientFallbackFactory().create(new RuntimeException("boom"));

        Result<?> statistics = client.getOrderStatistics();
        Result<?> trend = client.getOrderTrend(7);
        Result<?> topSales = client.getProductSalesTop(10);

        for (Result<?> result : List.of(statistics, trend, topSales)) {
            assertThat(result.getCode()).isEqualTo(500);
            assertThat(result.getMessage()).isEqualTo("OrderService unavailable");
            assertThat(result.getData()).isNull();
        }
    }

    @Test
    @DisplayName("商品侧降级：计数/分类映射/分类列表都返回 500，不能返回 0 或空集合冒充成功")
    void productClientShouldFailClosed() {
        ProductClient client = new ProductClientFallbackFactory().create(new RuntimeException("boom"));

        Result<?> count = client.countProducts();
        Result<?> categoryMap = client.getProductCategoryMap();
        Result<?> categories = client.getCategories();

        for (Result<?> result : List.of(count, categoryMap, categories)) {
            assertThat(result.getCode()).isEqualTo(500);
            assertThat(result.getMessage()).isEqualTo("ProductService unavailable");
            assertThat(result.getData()).isNull();
        }
    }

    @Test
    @DisplayName("用户侧降级：总数返回 500 且 data 为 null（不是 0）")
    void userClientShouldFailClosed() {
        UserClient client = new UserClientFallbackFactory().create(new RuntimeException("boom"));

        Result<Long> count = client.countUsers();

        assertThat(count.getCode()).isEqualTo(500);
        assertThat(count.getMessage()).isEqualTo("UserService unavailable");
        assertThat(count.getData()).isNull();
    }
}
