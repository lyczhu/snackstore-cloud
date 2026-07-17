package com.lawyus.snackstore.product.search.service.impl;

import java.math.BigDecimal;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import com.lawyus.snackstore.common.message.ProductSearchSyncMessage;
import com.lawyus.snackstore.common.response.PageResult;
import com.lawyus.snackstore.product.search.client.ProductDataClient;
import com.lawyus.snackstore.product.search.model.document.ProductSearchDocument;
import com.lawyus.snackstore.product.search.repository.ProductSearchRepository;
import com.lawyus.snackstore.product.search.service.ProductSearchIndexService;

@Service
public class ProductSearchIndexServiceImpl implements ProductSearchIndexService {

    private static final Logger log = LoggerFactory.getLogger(ProductSearchIndexServiceImpl.class);
    private static final int REBUILD_PAGE_SIZE = 1000;

    private final ProductSearchRepository productSearchRepository;
    private final ProductDataClient productDataClient;

    public ProductSearchIndexServiceImpl(ProductSearchRepository productSearchRepository,
                                         ProductDataClient productDataClient) {
        this.productSearchRepository = productSearchRepository;
        this.productDataClient = productDataClient;
    }

    @Override
    public void save(ProductSearchSyncMessage message) {
        if (message == null || message.getId() == null) {
            return;
        }
        productSearchRepository.save(buildDocument(message));
        log.debug("ES索引已保存: productId={}", message.getId());
    }

    @Override
    public void delete(Long productId) {
        if (productId == null) {
            return;
        }
        productSearchRepository.deleteById(productId);
        log.debug("ES索引已删除: productId={}", productId);
    }

    private ProductSearchDocument buildDocument(ProductSearchSyncMessage message) {
        ProductSearchDocument document = new ProductSearchDocument();
        document.setId(message.getId());
        document.setCategoryId(message.getCategoryId());
        document.setCategoryName(message.getCategoryName());
        document.setCategorySort(message.getCategorySort());
        document.setName(message.getName());
        document.setCoverImage(message.getCoverImage());
        if (message.getPrice() != null) {
            document.setPrice(message.getPrice().multiply(BigDecimal.valueOf(100)).doubleValue());
        }
        document.setStock(message.getStock());
        document.setDescription(message.getDescription());
        document.setStatus(message.getStatus());
        document.setCreatedAt(message.getCreatedAt());
        return document;
    }
}
