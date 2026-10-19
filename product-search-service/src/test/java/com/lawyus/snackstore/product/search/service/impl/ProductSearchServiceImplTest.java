package com.lawyus.snackstore.product.search.service.impl;

import co.elastic.clients.elasticsearch._types.query_dsl.BoolQuery;
import co.elastic.clients.elasticsearch._types.query_dsl.Query;
import com.lawyus.snackstore.common.dto.ProductSearchDTO;
import com.lawyus.snackstore.common.dto.ProductSearchItemDTO;
import com.lawyus.snackstore.common.response.PageResult;
import com.lawyus.snackstore.common.response.ResultCode;
import com.lawyus.snackstore.product.search.client.ProductDataClient;
import com.lawyus.snackstore.product.search.exception.BusinessException;
import com.lawyus.snackstore.product.search.model.document.ProductSearchDocument;
import com.lawyus.snackstore.product.search.model.vo.ProductSearchVO;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.elasticsearch.core.ElasticsearchOperations;
import org.springframework.data.elasticsearch.core.SearchHit;
import org.springframework.data.elasticsearch.core.SearchHits;
import org.springframework.data.elasticsearch.client.elc.NativeQuery;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * ES 搜索的特征化测试。这里锁四件事：排序白名单与深分页上限（防注入/防扫全索引）、
 * 默认只出上架商品的过滤、金额单位换算（ES 存分、接口用元）、以及降级路径的空结果兜底。
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("ProductSearchServiceImpl 查询构造与降级")
class ProductSearchServiceImplTest {

    @Mock
    private ElasticsearchOperations elasticsearchOperations;

    @Mock
    private ProductDataClient productDataClient;

    private ProductSearchServiceImpl service;

    @BeforeEach
    void setUp() {
        service = new ProductSearchServiceImpl(elasticsearchOperations, productDataClient);
    }

    private static ProductSearchDTO search(String keyword) {
        ProductSearchDTO dto = new ProductSearchDTO();
        dto.setKeyword(keyword);
        return dto;
    }

    private static ProductSearchDocument document(long id, String name, Double priceInCents, int status) {
        ProductSearchDocument doc = new ProductSearchDocument();
        doc.setId(id);
        doc.setName(name);
        doc.setPrice(priceInCents);
        doc.setStock(10);
        doc.setStatus(status);
        doc.setCategoryId(3L);
        doc.setCategoryName("饼干");
        return doc;
    }

    @SuppressWarnings("unchecked")
    private void stubEsHits(List<SearchHit<ProductSearchDocument>> hits, long total) {
        SearchHits<ProductSearchDocument> searchHits = mock(SearchHits.class);
        when(searchHits.getSearchHits()).thenReturn(hits);
        when(searchHits.getTotalHits()).thenReturn(total);
        when(elasticsearchOperations.search(
                any(org.springframework.data.elasticsearch.core.query.Query.class),
                eq(ProductSearchDocument.class))).thenReturn(searchHits);
    }

    @SuppressWarnings("unchecked")
    private SearchHit<ProductSearchDocument> hitOf(ProductSearchDocument doc,
                                                   Map<String, List<String>> highlights) {
        SearchHit<ProductSearchDocument> hit = mock(SearchHit.class);
        when(hit.getContent()).thenReturn(doc);
        when(hit.getHighlightFields()).thenReturn(highlights);
        return hit;
    }

    /** Spring Data 与 ES 客户端各有一个 Query，此处必须用全限定名避开同名冲突 */
    private BoolQuery capturedBoolQuery() {
        ArgumentCaptor<org.springframework.data.elasticsearch.core.query.Query> captor =
                ArgumentCaptor.forClass(org.springframework.data.elasticsearch.core.query.Query.class);
        verify(elasticsearchOperations).search(captor.capture(), eq(ProductSearchDocument.class));
        Query query = ((NativeQuery) captor.getValue()).getQuery();
        assertThat(query.isBool()).isTrue();
        return query.bool();
    }

    @Test
    @DisplayName("白名单外的排序字段直接拒绝，且不查询 ES")
    void rejectsSortFieldOutsideWhitelist() {
        ProductSearchDTO dto = search("饼干");
        dto.setSortField("password");

        assertThatThrownBy(() -> service.search(dto))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("不支持的排序字段: password");

        verifyNoInteractions(elasticsearchOperations);
    }

    @Test
    @DisplayName("深分页超过 from 上限时拒绝，并提示最大页数")
    void rejectsDeepPagination() {
        ProductSearchDTO dto = search("饼干");
        dto.setPageNum(1001);
        dto.setPageSize(20);

        assertThatThrownBy(() -> service.search(dto))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("分页深度超过限制");
    }

    @Test
    @DisplayName("不带任何筛选条件时也必须加 status 过滤（默认只出上架商品）")
    void alwaysFiltersStatusEvenWithoutExplicitCriteria() {
        stubEsHits(List.of(), 0L);

        service.search(search(null));

        List<Query> filters = capturedBoolQuery().filter();
        assertThat(filters).hasSize(1);
        assertThat(filters).anyMatch(q -> q.isTerm() && "status".equals(q.term().field()));
    }

    @Test
    @DisplayName("显式传 status=0 时不套用默认值，下架商品可被管理员查到")
    void explicitStatusOverridesDefault() {
        stubEsHits(List.of(), 0L);
        ProductSearchDTO dto = search("饼干");
        dto.setStatus(0);

        service.search(dto);

        assertThat(capturedBoolQuery().filter())
                .filteredOn(q -> q.isTerm() && "status".equals(q.term().field()))
                .hasSize(1);
    }

    @Test
    @DisplayName("关键词进 must，分类与价格进 filter，命中高亮")
    void keywordGoesToMustAndFiltersToFilter() {
        stubEsHits(List.of(), 0L);
        ProductSearchDTO dto = search("饼干");
        dto.setCategoryId(3L);
        dto.setMinPrice(new BigDecimal("9.90"));

        service.search(dto);

        BoolQuery bool = capturedBoolQuery();
        assertThat(bool.must()).hasSize(1);
        assertThat(bool.must().getFirst().isMultiMatch()).isTrue();
        assertThat(bool.filter()).hasSize(3);
        assertThat(bool.filter()).anyMatch(q -> q.isTerm() && "categoryId".equals(q.term().field()));
        assertThat(bool.filter()).anyMatch(Query::isRange);
    }

    @Test
    @DisplayName("ES 命中结果：分转元保留两位，高亮片段落到 VO")
    void convertsCentsBackToYuanAndAppliesHighlights() {
        ProductSearchDocument doc = document(1L, "饼干", 1299.0, 1);
        stubEsHits(List.of(hitOf(doc, Map.of("name", List.of("<em>饼</em>干")))), 7L);

        PageResult<ProductSearchVO> result = service.search(search("饼干"));

        assertThat(result.getTotal()).isEqualTo(7L);
        assertThat(result.getData()).hasSize(1);
        assertThat(result.getData().getFirst().getPrice()).isEqualByComparingTo("12.99");
        assertThat(result.getData().getFirst().getHighlightedName()).isEqualTo("<em>饼</em>干");
        assertThat(result.getData().getFirst().getHighlightedDescription()).isNull();
    }

    @Test
    @DisplayName("ES 抛任意异常时包装成 PRODUCT_SEARCH_ERROR")
    void esFailureIsWrappedAsBusinessException() {
        when(elasticsearchOperations.search(
                any(org.springframework.data.elasticsearch.core.query.Query.class),
                eq(ProductSearchDocument.class)))
                .thenThrow(new IllegalStateException("connect timeout"));

        assertThatThrownBy(() -> service.search(search("饼干")))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("connect timeout");
    }

    @Test
    @DisplayName("降级到 MySQL：内部接口返回 null 时给出空分页而不是 NPE")
    void fallbackReturnsEmptyPageWhenUpstreamIsNull() {
        when(productDataClient.searchFallback(any(ProductSearchDTO.class))).thenReturn(null);

        PageResult<ProductSearchVO> result = service.searchFallback(search("饼干"), new RuntimeException("es down"));

        assertThat(result.getData()).isEmpty();
        assertThat(result.getTotal()).isZero();
        assertThat(result.getPageNum()).isEqualTo(1L);
    }

    @Test
    @DisplayName("降级结果直接透传分页元数据，价格已是元不再换算")
    void fallbackPassesThroughPagingAndYuanPrice() {
        ProductSearchItemDTO item = new ProductSearchItemDTO();
        item.setId(1L);
        item.setName("饼干");
        item.setPrice(new BigDecimal("12.99"));
        item.setStatus(1);
        when(productDataClient.searchFallback(any(ProductSearchDTO.class)))
                .thenReturn(PageResult.success(List.of(item), 2L, 10L, 35L));

        PageResult<ProductSearchVO> result = service.searchFallback(search("饼干"), new RuntimeException("es down"));

        assertThat(result.getData().getFirst().getPrice()).isEqualByComparingTo("12.99");
        assertThat(result.getPageNum()).isEqualTo(2L);
        assertThat(result.getTotal()).isEqualTo(35L);
    }

    @Test
    @DisplayName("被 Sentinel 限流时返回服务降级错误码")
    void blockHandlerReportsServiceDegraded() {
        assertThatThrownBy(() -> service.searchBlockHandler(search("饼干"), null))
                .isInstanceOf(BusinessException.class)
                .hasMessage(ResultCode.SERVICE_DEGRADED.getMessage());
    }
}
