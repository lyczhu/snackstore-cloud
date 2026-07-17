package com.lawyus.snackstore.product.search.repository;

import com.lawyus.snackstore.product.search.model.document.ProductSearchDocument;
import org.springframework.data.elasticsearch.repository.ElasticsearchRepository;

public interface ProductSearchRepository extends ElasticsearchRepository<ProductSearchDocument, Long> {
}
