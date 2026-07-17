package com.lawyus.snackstore.product.search.config;

import co.elastic.clients.elasticsearch.ElasticsearchClient;
import co.elastic.clients.elasticsearch.indices.ExistsAliasRequest;
import co.elastic.clients.elasticsearch.indices.UpdateAliasesRequest;
import com.lawyus.snackstore.product.search.model.document.ProductSearchDocument;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.data.elasticsearch.core.ElasticsearchOperations;
import org.springframework.data.elasticsearch.core.IndexOperations;
import org.springframework.data.elasticsearch.core.document.Document;
import org.springframework.data.elasticsearch.core.mapping.IndexCoordinates;
import org.springframework.stereotype.Component;

@Component
public class ElasticsearchIndexConfig {

    private static final Logger log = LoggerFactory.getLogger(ElasticsearchIndexConfig.class);
    private static final String INDEX_NAME = "product_v1";
    private static final String ALIAS_NAME = "product";

    private final ElasticsearchOperations elasticsearchOperations;
    private final ElasticsearchClient elasticsearchClient;

    public ElasticsearchIndexConfig(ElasticsearchOperations elasticsearchOperations,
                                    ElasticsearchClient elasticsearchClient) {
        this.elasticsearchOperations = elasticsearchOperations;
        this.elasticsearchClient = elasticsearchClient;
    }

    @EventListener(ApplicationReadyEvent.class)
    public void setupIndexAndAlias() {
        try {
            IndexCoordinates indexCoordinates = IndexCoordinates.of(INDEX_NAME);
            IndexOperations indexOps = elasticsearchOperations.indexOps(indexCoordinates);

            boolean indexExists = indexOps.exists();
            if (!indexExists) {
                indexOps.create();
                log.info("ES索引已创建: {}", INDEX_NAME);
            }

            IndexOperations docIndexOps = elasticsearchOperations.indexOps(ProductSearchDocument.class);
            Document mapping = docIndexOps.createMapping();
            indexOps.putMapping(mapping);
            log.info("ES索引mapping已设置: {}", INDEX_NAME);

            boolean aliasExists = elasticsearchClient.indices()
                    .existsAlias(ExistsAliasRequest.of(e -> e.name(ALIAS_NAME).index(INDEX_NAME)))
                    .value();

            if (!aliasExists) {
                elasticsearchClient.indices().updateAliases(UpdateAliasesRequest.of(u -> u
                        .actions(a -> a.add(add -> add.index(INDEX_NAME).alias(ALIAS_NAME)))));
                log.info("ES别名已设置: {} -> {}", ALIAS_NAME, INDEX_NAME);
            } else {
                log.debug("ES别名已存在: {} -> {}", ALIAS_NAME, INDEX_NAME);
            }
        } catch (Exception e) {
            log.error("ES索引别名初始化失败", e);
        }
    }
}
