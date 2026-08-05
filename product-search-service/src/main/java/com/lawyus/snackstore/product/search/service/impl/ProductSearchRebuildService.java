package com.lawyus.snackstore.product.search.service.impl;

import com.lawyus.snackstore.common.message.ProductSearchSyncMessage;
import com.lawyus.snackstore.common.response.PageResult;
import com.lawyus.snackstore.product.search.client.ProductDataClient;
import com.lawyus.snackstore.product.search.model.document.ProductSearchDocument;
import com.lawyus.snackstore.product.search.service.ProductSearchIndexService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.elasticsearch.core.ElasticsearchOperations;
import org.springframework.data.elasticsearch.core.query.DeleteQuery;
import org.springframework.data.elasticsearch.core.query.Query;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;

@Component
public class ProductSearchRebuildService {

    private static final Logger log = LoggerFactory.getLogger(ProductSearchRebuildService.class);

    private static final long MAX_PAGES = 10000;

    private final ProductDataClient productDataClient;
    private final ProductSearchIndexService searchIndexService;
    private final ElasticsearchOperations elasticsearchOperations;
    private final AtomicBoolean rebuilding = new AtomicBoolean(false);

    @Value("${product-search.rebuild.page-size:100}")
    private int pageSize;

    public ProductSearchRebuildService(ProductDataClient productDataClient,
                                       ProductSearchIndexService searchIndexService,
                                       ElasticsearchOperations elasticsearchOperations) {
        this.productDataClient = productDataClient;
        this.searchIndexService = searchIndexService;
        this.elasticsearchOperations = elasticsearchOperations;
    }

    @Scheduled(cron = "${product-search.rebuild.cron:0 0 3 * * *}")
    public void scheduledRebuild() {
        log.info("开始定时全量重建ES索引");
        try {
            rebuildAll();
        } catch (Exception e) {
            log.error("定时全量重建ES索引失败", e);
        }
    }

    public void rebuildAll() {
        if (!rebuilding.compareAndSet(false, true)) {
            log.warn("ES索引全量重建正在进行中，跳过本次触发");
            return;
        }
        long startTime = System.currentTimeMillis();
        try {
            DeleteQuery deleteQuery = DeleteQuery.builder(Query.findAll()).build();
            elasticsearchOperations.delete(deleteQuery, ProductSearchDocument.class);

            int pageNum = 1;
            long savedCount = 0;
            long total = 0;
            do {
                PageResult<ProductSearchSyncMessage> page =
                        productDataClient.listForSearch("rebuild", pageNum, pageSize);
                List<ProductSearchSyncMessage> records = page.getData();
                if (records == null || records.isEmpty()) {
                    break;
                }
                searchIndexService.saveAll(records);
                savedCount += records.size();
                total = page.getTotal();
                pageNum++;
            } while (savedCount < total && pageNum <= MAX_PAGES);

            elasticsearchOperations.indexOps(ProductSearchDocument.class).refresh();
            log.info("ES索引全量重建完成: 共{}条, 耗时{}ms", savedCount, System.currentTimeMillis() - startTime);
        } catch (Exception e) {
            log.error("ES索引全量重建失败", e);
            throw new IllegalStateException("ES索引全量重建失败", e);
        } finally {
            rebuilding.set(false);
        }
    }
}
