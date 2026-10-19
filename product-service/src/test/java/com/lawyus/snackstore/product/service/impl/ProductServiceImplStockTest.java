package com.lawyus.snackstore.product.service.impl;

import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.conditions.Wrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import com.lawyus.snackstore.product.exception.BusinessException;
import com.lawyus.snackstore.product.model.dto.BatchStockDTO;
import com.lawyus.snackstore.product.model.dto.StockDTO;
import com.lawyus.snackstore.product.model.entity.Product;
import com.lawyus.snackstore.product.model.event.ProductChangedEvent;
import com.lawyus.snackstore.product.model.event.ProductChangedEvent.ChangeType;
import com.lawyus.snackstore.product.repository.ProductMapper;
import com.lawyus.snackstore.product.service.ProductCategoryService;
import com.lawyus.snackstore.product.service.ProductSnapshotService;
import org.apache.ibatis.builder.MapperBuilderAssistant;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * 库存扣减与回滚的特征化测试。
 *
 * <p>这是库存补偿方案（docs/库存补偿方案.md）S1 切片要改动的类，所以先把「现在的行为」钉死：
 * 扣减靠 `WHERE stock >= n` 的条件更新防超卖、预检查先于更新、失败必须不发事件、
 * 以及**单商品回滚检查影响行数而批量回滚不检查**这一处不对称。S1 加流水表后这些语义都不允许变。
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("ProductServiceImpl 库存增减的原子性与事件语义")
class ProductServiceImplStockTest {

    @Mock
    private ProductMapper productMapper;

    @Mock
    private ProductCategoryService categoryService;

    @Mock
    private ProductSnapshotService snapshotService;

    @Mock
    private ApplicationEventPublisher eventPublisher;

    @InjectMocks
    private ProductServiceImpl productService;

    @BeforeAll
    static void installLambdaColumnCache() {
        MapperBuilderAssistant assistant =
                new MapperBuilderAssistant(new MybatisConfiguration(), "");
        assistant.setCurrentNamespace(ProductMapper.class.getName());
        TableInfoHelper.initTableInfo(assistant, Product.class);
    }

    private static Product product(long id, int stock) {
        Product p = new Product();
        p.setId(id);
        p.setName("零食-" + id);
        p.setStock(stock);
        p.setStatus(1);
        return p;
    }

    private static StockDTO stockItem(long productId, int quantity) {
        StockDTO item = new StockDTO();
        item.setProductId(productId);
        item.setQuantity(quantity);
        return item;
    }

    private static BatchStockDTO batch(String orderNo, StockDTO... items) {
        BatchStockDTO dto = new BatchStockDTO();
        dto.setOrderNo(orderNo);
        dto.setItems(List.of(items));
        return dto;
    }

    @Test
    @DisplayName("单商品扣减：数量为空或小于 1 直接拒绝，且不产生任何更新")
    void deductRejectsNonPositiveQuantity() {
        assertThatThrownBy(() -> productService.deductStock(1L, null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("数量必须大于0");
        assertThatThrownBy(() -> productService.deductStock(1L, 0))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("数量必须大于0");

        verifyNoInteractions(productMapper);
        verifyNoInteractions(eventPublisher);
    }

    @Test
    @DisplayName("单商品扣减用 WHERE stock >= n 的条件更新防超卖，且扣减表达式带数量")
    void deductUsesAtomicStockGuard() {
        when(productMapper.update(isNull(), any())).thenReturn(1);

        boolean ok = productService.deductStock(7L, 3);

        assertThat(ok).isTrue();
        ArgumentCaptor<Wrapper<Product>> captor = ArgumentCaptor.forClass(Wrapper.class);
        verify(productMapper).update(isNull(), captor.capture());
        LambdaUpdateWrapper<Product> wrapper = (LambdaUpdateWrapper<Product>) captor.getValue();

        assertThat(wrapper.getSqlSegment()).contains("id =").contains("stock >=");
        assertThat(wrapper.getSqlSet()).contains("stock = stock - 3");
        verify(eventPublisher).publishEvent(new ProductChangedEvent(7L, ChangeType.STOCK_CHANGED));
    }

    @Test
    @DisplayName("单商品扣减影响 0 行（库存不足或并发抢先）返回 false 且不发索引同步事件")
    void deductFailureDoesNotPublishEvent() {
        when(productMapper.update(isNull(), any())).thenReturn(0);

        assertThat(productService.deductStock(7L, 99)).isFalse();
        verifyNoInteractions(eventPublisher);
    }

    @Test
    @DisplayName("单商品回滚：商品不存在（0 行）返回 false")
    void rollbackDetectsMissingProduct() {
        when(productMapper.update(isNull(), any())).thenReturn(0);

        assertThat(productService.rollbackStock(7L, 3)).isFalse();
        verifyNoInteractions(eventPublisher);
    }

    @Test
    @DisplayName("单商品回滚用 stock = stock + n，无库存上限条件")
    void rollbackAddsStock() {
        when(productMapper.update(isNull(), any())).thenReturn(1);

        assertThat(productService.rollbackStock(7L, 5)).isTrue();

        ArgumentCaptor<Wrapper<Product>> captor = ArgumentCaptor.forClass(Wrapper.class);
        verify(productMapper).update(isNull(), captor.capture());
        LambdaUpdateWrapper<Product> wrapper = (LambdaUpdateWrapper<Product>) captor.getValue();

        assertThat(wrapper.getSqlSet()).contains("stock = stock + 5");
        assertThat(wrapper.getSqlSegment()).doesNotContain("stock >=");
        verify(eventPublisher).publishEvent(new ProductChangedEvent(7L, ChangeType.STOCK_CHANGED));
    }

    @Test
    @DisplayName("批量扣减：预检查发现商品不存在时抛错，且一次更新都不执行")
    void batchDeductPrecheckRejectsUnknownProduct() {
        when(productMapper.selectByIds(anyCollection())).thenReturn(List.of(product(1L, 50)));

        assertThatThrownBy(() -> productService.batchDeductStock(batch("NO-1", stockItem(1L, 2), stockItem(7L, 2))))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("商品 7 不存在");

        verify(productMapper, never()).update(any(), any());
        verifyNoInteractions(eventPublisher);
    }

    @Test
    @DisplayName("批量扣减：预检查发现库存不足时抛错，且一次更新都不执行")
    void batchDeductPrecheckRejectsInsufficientStock() {
        when(productMapper.selectByIds(anyCollection()))
                .thenReturn(List.of(product(1L, 50), product(2L, 1)));

        assertThatThrownBy(() -> productService.batchDeductStock(batch("NO-1", stockItem(1L, 2), stockItem(2L, 5))))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("商品 2 库存不足");

        verify(productMapper, never()).update(any(), any());
        verifyNoInteractions(eventPublisher);
    }

    @Test
    @DisplayName("批量扣减成功：每个商品各一次条件更新，各发一次 STOCK_CHANGED")
    void batchDeductSuccessUpdatesAndPublishesPerItem() {
        when(productMapper.selectByIds(anyCollection()))
                .thenReturn(List.of(product(1L, 50), product(2L, 50)));
        when(productMapper.update(isNull(), any())).thenReturn(1);

        assertThat(productService.batchDeductStock(batch("NO-1", stockItem(1L, 2), stockItem(2L, 3)))).isTrue();

        verify(productMapper, times(2)).update(isNull(), any());
        verify(eventPublisher).publishEvent(new ProductChangedEvent(1L, ChangeType.STOCK_CHANGED));
        verify(eventPublisher).publishEvent(new ProductChangedEvent(2L, ChangeType.STOCK_CHANGED));
    }

    @Test
    @DisplayName("批量扣减：预检查通过但更新影响 0 行（并发抢先）时抛「请重试」且不发事件")
    void batchDeductHandlesRaceCondition() {
        when(productMapper.selectByIds(anyCollection()))
                .thenReturn(List.of(product(1L, 50), product(2L, 50)));
        when(productMapper.update(isNull(), any())).thenReturn(1).thenReturn(0);

        assertThatThrownBy(() -> productService.batchDeductStock(batch("NO-1", stockItem(1L, 2), stockItem(2L, 3))))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("库存不足，请重试");

        verify(eventPublisher, never()).publishEvent(any(ProductChangedEvent.class));
    }

    @Test
    @DisplayName("批量回滚不检查影响行数：商品不存在也返回 true（与单商品回滚不对称，S1 需收口的点）")
    void batchRollbackIgnoresAffectedRows() {
        when(productMapper.update(isNull(), any())).thenReturn(0);

        assertThat(productService.batchRollbackStock(batch("NO-1", stockItem(1L, 2)))).isTrue();

        // 即便一行没更新，事件仍然照发，索引会被推动重算
        verify(eventPublisher).publishEvent(new ProductChangedEvent(1L, ChangeType.STOCK_CHANGED));
    }

    @Test
    @DisplayName("批量回滚逐商品执行并逐商品发事件")
    void batchRollbackPerItem() {
        when(productMapper.update(isNull(), any())).thenReturn(1);

        assertThat(productService.batchRollbackStock(
                batch("NO-1", stockItem(1L, 2), stockItem(2L, 3)))).isTrue();

        verify(productMapper, times(2)).update(isNull(), any());
        verify(eventPublisher, times(2)).publishEvent(any(ProductChangedEvent.class));
    }
}
