package com.lawyus.snackstore.product.search.service.impl;

import com.lawyus.snackstore.common.dto.ProductSearchItemDTO;
import com.lawyus.snackstore.common.response.PageResult;
import com.lawyus.snackstore.product.search.client.ProductDataClient;
import com.lawyus.snackstore.product.search.model.document.ProductSearchDocument;
import com.lawyus.snackstore.product.search.service.ProductSearchIndexService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.elasticsearch.core.ElasticsearchOperations;
import org.springframework.data.elasticsearch.core.IndexOperations;
import org.springframework.data.elasticsearch.core.query.DeleteQuery;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * {@link ProductSearchRebuildService} 的单元测试。
 *
 * <p>这条链路是「每晚 03:00 全量重建」——按方案它必须保留为兜底收敛器，所以它的行为要钉死三件事：
 * 一次删空重建的<b>顺序</b>、重入时<b>只有一份在跑</b>、失败后<b>标志位必须复位</b>（否则再也不会重建），
 * 以及页数上限<b>不会死循环</b>。
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("搜索索引全量重建：顺序、单实例、失败复位与页数上限")
class ProductSearchRebuildServiceTest {

    @Mock
    private ProductDataClient productDataClient;

    @Mock
    private ProductSearchIndexService searchIndexService;

    @Mock
    private ElasticsearchOperations elasticsearchOperations;

    private ProductSearchRebuildService service;

    @BeforeEach
    void setUp() {
        service = new ProductSearchRebuildService(productDataClient, searchIndexService, elasticsearchOperations);
        ReflectionTestUtils.setField(service, "pageSize", 2);
    }

    private PageResult<ProductSearchItemDTO> page(long total, ProductSearchItemDTO... records) {
        return PageResult.success(java.util.Arrays.asList(records), 1L, 2L, total);
    }

    private ProductSearchItemDTO item(long id) {
        ProductSearchItemDTO dto = new ProductSearchItemDTO();
        dto.setId(id);
        dto.setStatus(1);
        return dto;
    }

    private void stubRefresh() {
        IndexOperations indexOps = mock(IndexOperations.class);
        when(elasticsearchOperations.indexOps(ProductSearchDocument.class)).thenReturn(indexOps);
    }

    @Test
    @DisplayName("必须先删空索引，再分页写入，最后 refresh")
    void rebuildShouldDeleteThenPageThenRefresh() {
        stubRefresh();
        when(productDataClient.listForSearch(anyInt(), anyInt()))
                .thenReturn(page(3, item(1L), item(2L)))
                .thenReturn(page(3, item(3L)))
                .thenReturn(page(0));

        service.rebuildAll();

        InOrder inOrder = inOrder(elasticsearchOperations, searchIndexService);
        inOrder.verify(elasticsearchOperations).delete(any(DeleteQuery.class),
                org.mockito.ArgumentMatchers.eq(ProductSearchDocument.class));
        inOrder.verify(searchIndexService, times(2)).saveAll(any());
        inOrder.verify(elasticsearchOperations).indexOps(ProductSearchDocument.class);

        // ProductSearchItemDTO 无 equals，只能比内容不能比对象
        @SuppressWarnings("unchecked")
        org.mockito.ArgumentCaptor<List<ProductSearchItemDTO>> captor =
                org.mockito.ArgumentCaptor.forClass(List.class);
        verify(searchIndexService, times(2)).saveAll(captor.capture());
        assertThat(captor.getAllValues()).extracting(List::size).containsExactly(2, 1);
        assertThat(captor.getAllValues().stream().flatMap(List::stream).map(ProductSearchItemDTO::getId).toList())
                .containsExactly(1L, 2L, 3L);
    }

    @Test
    @DisplayName("分页游标逐页递增请求，直到已写入数达到 total 后停止")
    void rebuildShouldAdvancePageNumberAndStopAtTotal() {
        stubRefresh();
        // total=4：第一页只有 2 条，必须继续翻第二页；写满 4 条后退出
        when(productDataClient.listForSearch(anyInt(), anyInt()))
                .thenReturn(page(4, item(1L), item(2L)))
                .thenReturn(page(4, item(3L), item(4L)));

        service.rebuildAll();

        verify(productDataClient).listForSearch(1, 2);
        verify(productDataClient).listForSearch(2, 2);
        verify(productDataClient, never()).listForSearch(org.mockito.ArgumentMatchers.eq(3), anyInt());
        verify(searchIndexService, times(2)).saveAll(any());
    }

    @Test
    @DisplayName("首页即为空时仍然 refresh，不去调用 saveAll")
    void rebuildShouldRefreshEvenWhenNothingToIndex() {
        stubRefresh();
        when(productDataClient.listForSearch(1, 2)).thenReturn(page(0));

        service.rebuildAll();

        verify(searchIndexService, never()).saveAll(any());
        verify(elasticsearchOperations).delete(any(DeleteQuery.class),
                org.mockito.ArgumentMatchers.eq(ProductSearchDocument.class));
    }

    @Test
    @DisplayName("重建进行中再次触发必须直接跳过，不能出现两份并行的删空重建")
    void reentrantTriggerShouldBeSkipped() {
        // 第一次 listForSearch 时重入调用 rebuildAll：因 rebuilding 标志位占用，重入应立即返回
        when(productDataClient.listForSearch(anyInt(), anyInt())).thenAnswer(invocation -> {
            service.rebuildAll();
            return page(1, item(1L));
        });
        IndexOperations indexOps = mock(IndexOperations.class);
        when(elasticsearchOperations.indexOps(ProductSearchDocument.class)).thenReturn(indexOps);

        service.rebuildAll();

        // 重入没有再删一次索引，也没有继续翻页
        verify(elasticsearchOperations, times(1)).delete(any(DeleteQuery.class),
                org.mockito.ArgumentMatchers.eq(ProductSearchDocument.class));
        verify(productDataClient, times(1)).listForSearch(anyInt(), anyInt());
    }

    @Test
    @DisplayName("重建失败要抛出，但必须复位标志位，否则后续永远不会再重建")
    void rebuildShouldResetFlagAfterFailure() {
        when(elasticsearchOperations.delete(any(DeleteQuery.class),
                org.mockito.ArgumentMatchers.eq(ProductSearchDocument.class)))
                .thenThrow(new IllegalStateException("es down"));

        assertThatThrownBy(() -> service.rebuildAll())
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("ES索引全量重建失败");

        // 标志位已复位：第二次触发能真正进入删索引逻辑（否则 CAS 失败会静默返回，delete 只会调用一次）
        assertThatThrownBy(() -> service.rebuildAll())
                .isInstanceOf(IllegalStateException.class);
        verify(elasticsearchOperations, times(2)).delete(any(DeleteQuery.class),
                org.mockito.ArgumentMatchers.eq(ProductSearchDocument.class));
    }

    @Test
    @DisplayName("定时入口吞掉异常，不能让调度线程因一次重建失败而中断")
    void scheduledRebuildShouldSwallowFailure() {
        when(elasticsearchOperations.delete(any(DeleteQuery.class),
                org.mockito.ArgumentMatchers.eq(ProductSearchDocument.class)))
                .thenThrow(new IllegalStateException("es down"));

        assertThatCode(() -> service.scheduledRebuild()).doesNotThrowAnyException();
    }

    @Test
    @DisplayName("total 虚高、每页都有数据时，靠 MAX_PAGES 上限退出而不是死循环")
    void rebuildShouldStopAtMaxPagesGuard() {
        stubRefresh();
        // pageSize 设为 1，total 远超 1 * MAX_PAGES，且每页都返回一条数据
        ReflectionTestUtils.setField(service, "pageSize", 1);
        when(productDataClient.listForSearch(anyInt(), anyInt())).thenReturn(page(1_000_000, item(1L)));

        service.rebuildAll();

        verify(productDataClient, times(10_000)).listForSearch(anyInt(), anyInt());
        verify(searchIndexService, times(10_000)).saveAll(any());
    }
}
