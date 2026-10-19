package com.lawyus.snackstore.product.search.service.impl;

import com.lawyus.snackstore.common.dto.ProductSearchItemDTO;
import com.lawyus.snackstore.product.search.model.document.ProductSearchDocument;
import com.lawyus.snackstore.product.search.repository.ProductSearchRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

/**
 * 索引写入的特征化测试：这是「拉取通道」把商品快照转成 ES 文档的唯一入口。
 * 锁住三件事：脏数据（null 或无 ID）不得入索引、金额按「元转分」入库、删除必须按商品 ID 精确执行。
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("ProductSearchIndexServiceImpl 索引写入")
class ProductSearchIndexServiceImplTest {

    @Mock
    private ProductSearchRepository productSearchRepository;

    private ProductSearchIndexServiceImpl service;

    @BeforeEach
    void setUp() {
        service = new ProductSearchIndexServiceImpl(productSearchRepository);
    }

    private static ProductSearchItemDTO item(Long id, String price) {
        ProductSearchItemDTO dto = new ProductSearchItemDTO();
        dto.setId(id);
        dto.setName("饼干");
        dto.setCategoryId(3L);
        dto.setCategoryName("饼干类");
        dto.setCategorySort(2);
        dto.setCoverImage("http://img/1");
        dto.setPrice(price == null ? null : new BigDecimal(price));
        dto.setStock(10);
        dto.setDescription("入口即化");
        dto.setStatus(1);
        dto.setCreatedAt(LocalDateTime.of(2026, 10, 19, 10, 0));
        return dto;
    }

    @Test
    @DisplayName("null 项与无 ID 项直接忽略，不产生索引写入")
    void ignoresNullItemAndNullId() {
        service.save(null);
        service.save(item(null, "9.90"));

        verifyNoInteractions(productSearchRepository);
    }

    @Test
    @DisplayName("价格按元转分写入文档，其余快照字段透传")
    void convertsYuanToCentsAndCopiesFields() {
        service.save(item(1L, "12.99"));

        ArgumentCaptor<ProductSearchDocument> captor = ArgumentCaptor.forClass(ProductSearchDocument.class);
        verify(productSearchRepository).save(captor.capture());
        ProductSearchDocument doc = captor.getValue();

        assertThat(doc.getId()).isEqualTo(1L);
        assertThat(doc.getPrice()).isEqualTo(1299.0);
        assertThat(doc.getName()).isEqualTo("饼干");
        assertThat(doc.getCategoryName()).isEqualTo("饼干类");
        assertThat(doc.getStatus()).isEqualTo(1);
        assertThat(doc.getCreatedAt()).isEqualTo(LocalDateTime.of(2026, 10, 19, 10, 0));
    }

    @Test
    @DisplayName("价格为 null 时不写入换算值，避免 NPE 打断整批重建")
    void nullPriceDoesNotBreakConversion() {
        service.save(item(1L, null));

        ArgumentCaptor<ProductSearchDocument> captor = ArgumentCaptor.forClass(ProductSearchDocument.class);
        verify(productSearchRepository).save(captor.capture());
        assertThat(captor.getValue().getPrice()).isNull();
    }

    @Test
    @DisplayName("批量写入过滤 null 元素与无 ID 元素")
    void saveAllFiltersDirtyItems() {
        service.saveAll(Arrays.asList(item(1L, "1.00"), null, item(null, "2.00")));

        @SuppressWarnings("unchecked")
        ArgumentCaptor<List<ProductSearchDocument>> captor = ArgumentCaptor.forClass(List.class);
        verify(productSearchRepository).saveAll(captor.capture());
        assertThat(captor.getValue()).hasSize(1);
    }

    @Test
    @DisplayName("空列表与 null 列表不产生任何索引操作")
    void saveAllSkipsEmptyList() {
        service.saveAll(List.of());
        service.saveAll(null);

        verifyNoInteractions(productSearchRepository);
    }

    @Test
    @DisplayName("删除：ID 为空忽略，否则按 ID 精确删除")
    void deleteRequiresId() {
        service.delete(null);
        verifyNoInteractions(productSearchRepository);

        service.delete(7L);
        verify(productSearchRepository).deleteById(7L);
    }
}
