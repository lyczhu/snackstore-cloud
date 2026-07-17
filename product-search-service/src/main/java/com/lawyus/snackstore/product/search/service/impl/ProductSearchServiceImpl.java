package com.lawyus.snackstore.product.search.service.impl;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;

import co.elastic.clients.elasticsearch._types.SortOrder;
import co.elastic.clients.elasticsearch._types.query_dsl.Query;
import com.lawyus.snackstore.product.search.exception.BusinessException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.elasticsearch.client.elc.NativeQuery;
import org.springframework.data.elasticsearch.client.elc.NativeQueryBuilder;
import org.springframework.data.elasticsearch.core.ElasticsearchOperations;
import org.springframework.data.elasticsearch.core.SearchHits;
import org.springframework.data.elasticsearch.core.query.HighlightQuery;
import org.springframework.data.elasticsearch.core.query.highlight.Highlight;
import org.springframework.data.elasticsearch.core.query.highlight.HighlightField;
import org.springframework.data.elasticsearch.core.query.highlight.HighlightFieldParameters;
import org.springframework.data.elasticsearch.core.query.highlight.HighlightParameters;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import com.alibaba.csp.sentinel.annotation.SentinelResource;
import com.alibaba.csp.sentinel.slots.block.BlockException;
import com.lawyus.snackstore.common.dto.ProductSearchDTO;
import com.lawyus.snackstore.common.message.ProductSearchSyncMessage;
import com.lawyus.snackstore.common.response.PageResult;
import com.lawyus.snackstore.common.response.ResultCode;
import com.lawyus.snackstore.product.search.client.ProductDataClient;
import com.lawyus.snackstore.product.search.exception.BusinessExceptionEnum;
import com.lawyus.snackstore.product.search.model.document.ProductSearchDocument;
import com.lawyus.snackstore.product.search.model.vo.ProductSearchVO;
import com.lawyus.snackstore.product.search.service.ProductSearchService;

@Service
public class ProductSearchServiceImpl implements ProductSearchService {

    private static final Logger log = LoggerFactory.getLogger(ProductSearchServiceImpl.class);
    private static final Set<String> ALLOWED_SORT_FIELDS = Set.of("price", "createdAt", "id");
    private static final long MAX_FROM = 10000;
    private static final Pattern ES_SPECIAL_CHARS = Pattern.compile("([+\\-=&|!(){}\\[\\]^\"~*?:\\\\/])");

    private final ElasticsearchOperations elasticsearchOperations;
    private final ProductDataClient productDataClient;

    public ProductSearchServiceImpl(ElasticsearchOperations elasticsearchOperations,
                                    ProductDataClient productDataClient) {
        this.elasticsearchOperations = elasticsearchOperations;
        this.productDataClient = productDataClient;
    }

    @Override
    @SentinelResource(value = "productSearch",
            fallback = "searchFallback",
            blockHandler = "searchBlockHandler")
    public PageResult<ProductSearchVO> search(ProductSearchDTO dto) {
        validateSortField(dto.getSortField());
        validateDeepPagination(dto);

        long startTime = System.currentTimeMillis();
        try {
            Query query = buildQuery(dto);
            NativeQueryBuilder queryBuilder = NativeQuery.builder()
                    .withQuery(query)
                    .withPageable(PageRequest.of(dto.getPageNum() - 1, dto.getPageSize()));
            if (StringUtils.hasText(dto.getSortField())) {
                SortOrder sortOrder = "asc"
                        .equalsIgnoreCase(dto.getSortOrder()) ? SortOrder.Asc
                        : co.elastic.clients.elasticsearch._types.SortOrder.Desc;
                queryBuilder.withSort(s -> s.field(f -> f.field(dto.getSortField()).order(sortOrder)));
            }
            if (StringUtils.hasText(dto.getKeyword())) {
                HighlightFieldParameters nameParams = HighlightFieldParameters.builder()
                        .withPreTags("<em>").withPostTags("</em>").build();
                HighlightFieldParameters descParams = HighlightFieldParameters.builder()
                        .withPreTags("<em>").withPostTags("</em>").withFragmentSize(50).build();
                HighlightQuery highlightQuery = new HighlightQuery(
                        new Highlight(
                                HighlightParameters.builder().build(),
                                List.of(
                                        new HighlightField("name", nameParams),
                                        new HighlightField("description", descParams))),
                        ProductSearchDocument.class);
                queryBuilder.withHighlightQuery(highlightQuery);
            }
            NativeQuery nativeQuery = queryBuilder.build();
            log.debug("ES搜索请求: keyword={}, categoryId={}, status={}, minPrice={}, maxPrice={}, page={}/{}",
                    dto.getKeyword(), dto.getCategoryId(), dto.getStatus(),
                    dto.getMinPrice(), dto.getMaxPrice(), dto.getPageNum(), dto.getPageSize());


            SearchHits<ProductSearchDocument> searchHits = elasticsearchOperations.search(nativeQuery,
                    ProductSearchDocument.class);
            List<ProductSearchVO> data = searchHits.getSearchHits().stream()
                    .map(hit -> convertToVO(hit.getContent(), hit.getHighlightFields()))
                    .toList();

            long elapsed = System.currentTimeMillis() - startTime;
            log.info("ES搜索完成: keyword={}, 命中数={}, 耗时{}ms",
                    dto.getKeyword(), searchHits.getTotalHits(), elapsed);
            return PageResult.success(data, (long) dto.getPageNum(), (long) dto.getPageSize(),
                    searchHits.getTotalHits());
        } catch (IllegalArgumentException e) {
            throw e;
        } catch (Exception e) {
            long elapsed = System.currentTimeMillis() - startTime;
            log.error("ES搜索异常: keyword={}, 耗时{}ms", dto.getKeyword(), elapsed, e);
            throw BusinessExceptionEnum.PRODUCT_SEARCH_ERROR.getException(e.getMessage());
        }
    }

    public PageResult<ProductSearchVO> searchFallback(ProductSearchDTO dto, Throwable t) {
        log.warn("ES搜索降级到MySQL: keyword={}, cause={}", dto.getKeyword(), t.getMessage());
        PageResult<ProductSearchSyncMessage> fallbackResult = productDataClient.searchFallback(dto);
        if (fallbackResult == null || fallbackResult.getData() == null) {
            return PageResult.success(List.of(), (long) dto.getPageNum(), (long) dto.getPageSize(), 0L);
        }
        List<ProductSearchVO> data = fallbackResult.getData().stream()
                .map(this::convertToVO)
                .toList();
        return PageResult.success(data, fallbackResult.getPageNum(), fallbackResult.getPageSize(),
                fallbackResult.getTotal());
    }

    public PageResult<ProductSearchVO> searchBlockHandler(ProductSearchDTO dto, BlockException e) {
        log.warn("ES搜索被Sentinel限流: keyword={}", dto.getKeyword());
        throw new BusinessException(
                ResultCode.SERVICE_DEGRADED.getCode(), ResultCode.SERVICE_DEGRADED.getMessage());
    }

    private void validateDeepPagination(ProductSearchDTO dto) {
        if ((long) dto.getPageNum() * dto.getPageSize() > MAX_FROM) {
            throw new IllegalArgumentException("分页深度超过限制，最大支持" + (MAX_FROM / dto.getPageSize()) + "页");
        }
    }

    private void validateSortField(String sortField) {
        if (StringUtils.hasText(sortField) && !ALLOWED_SORT_FIELDS.contains(sortField)) {
            throw new IllegalArgumentException("不支持的排序字段: " + sortField);
        }
    }

    private String escapeKeyword(String keyword) {
        if (keyword == null) {
            return null;
        }
        return ES_SPECIAL_CHARS.matcher(keyword).replaceAll("\\\\$1");
    }

    private Query buildQuery(ProductSearchDTO dto) {
        List<Query> mustQueries = new ArrayList<>();
        if (StringUtils.hasText(dto.getKeyword())) {
            String escaped = escapeKeyword(dto.getKeyword());
            mustQueries.add(Query.of(q -> q.multiMatch(
                    mm -> mm.fields("name", "description").query(escaped))));
        }

        List<Query> filterQueries = new ArrayList<>();
        if (dto.getCategoryId() != null) {
            filterQueries.add(Query.of(
                    q -> q.term(t -> t.field("categoryId").value(dto.getCategoryId()))));
        }
        if (dto.getStatus() != null) {
            filterQueries.add(Query.of(
                    q -> q.term(t -> t.field("status").value(dto.getStatus()))));
        }
        if (dto.getMinPrice() != null || dto.getMaxPrice() != null) {
            filterQueries.add(buildPriceRangeQuery(dto));
        }

        if (mustQueries.isEmpty() && filterQueries.isEmpty()) {
            return Query.of(q -> q.matchAll(m -> m));
        }

        return Query.of(q -> q.bool(b -> {
            if (!mustQueries.isEmpty()) {
                b.must(mustQueries);
            }
            if (!filterQueries.isEmpty()) {
                b.filter(filterQueries);
            }
            return b;
        }));
    }

    private Query buildPriceRangeQuery(ProductSearchDTO dto) {
        return Query.of(q -> q.range(r -> r.number(n -> {
            n.field("price");
            if (dto.getMinPrice() != null) {
                n.gte(dto.getMinPrice().multiply(BigDecimal.valueOf(100)).doubleValue());
            }
            if (dto.getMaxPrice() != null) {
                n.lte(dto.getMaxPrice().multiply(BigDecimal.valueOf(100)).doubleValue());
            }
            return n;
        })));
    }

    private ProductSearchVO convertToVO(ProductSearchDocument document,
                                        Map<String, List<String>> highlightFields) {
        ProductSearchVO vo = convertToVO(document);
        if (highlightFields != null) {
            List<String> nameHighlights = highlightFields.get("name");
            if (nameHighlights != null && !nameHighlights.isEmpty()) {
                vo.setHighlightedName(nameHighlights.getFirst());
            }
            List<String> descHighlights = highlightFields.get("description");
            if (descHighlights != null && !descHighlights.isEmpty()) {
                vo.setHighlightedDescription(descHighlights.getFirst());
            }
        }
        return vo;
    }

    private ProductSearchVO convertToVO(ProductSearchSyncMessage message) {
        ProductSearchVO vo = new ProductSearchVO();
        vo.setId(message.getId());
        vo.setCategoryId(message.getCategoryId());
        vo.setCategoryName(message.getCategoryName());
        vo.setCategorySort(message.getCategorySort());
        vo.setName(message.getName());
        vo.setCoverImage(message.getCoverImage());
        vo.setPrice(message.getPrice());
        vo.setStock(message.getStock());
        vo.setDescription(message.getDescription());
        vo.setStatus(message.getStatus());
        return vo;
    }

    private ProductSearchVO convertToVO(ProductSearchDocument document) {
        ProductSearchVO vo = new ProductSearchVO();
        vo.setId(document.getId());
        vo.setCategoryId(document.getCategoryId());
        vo.setCategoryName(document.getCategoryName());
        vo.setCategorySort(document.getCategorySort());
        vo.setName(document.getName());
        vo.setCoverImage(document.getCoverImage());
        if (document.getPrice() != null) {
            vo.setPrice(BigDecimal.valueOf(document.getPrice())
                    .divide(BigDecimal.valueOf(100), 2, RoundingMode.HALF_UP));
        }
        vo.setStock(document.getStock());
        vo.setDescription(document.getDescription());
        vo.setStatus(document.getStatus());
        return vo;
    }
}
