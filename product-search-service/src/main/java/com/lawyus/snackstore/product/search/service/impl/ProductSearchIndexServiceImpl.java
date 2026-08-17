package com.lawyus.snackstore.product.search.service.impl;

import com.lawyus.snackstore.common.dto.ProductSearchItemDTO;
import com.lawyus.snackstore.product.search.model.document.ProductSearchDocument;
import com.lawyus.snackstore.product.search.repository.ProductSearchRepository;
import com.lawyus.snackstore.product.search.service.ProductSearchIndexService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.List;

@Service
public class ProductSearchIndexServiceImpl implements ProductSearchIndexService {

    private static final Logger log = LoggerFactory.getLogger(ProductSearchIndexServiceImpl.class);

    private final ProductSearchRepository productSearchRepository;

    public ProductSearchIndexServiceImpl(ProductSearchRepository productSearchRepository) {
        this.productSearchRepository = productSearchRepository;
    }

    @Override
    public void save(ProductSearchItemDTO item) {
        if (item == null || item.getId() == null) {
            return;
        }
        productSearchRepository.save(buildDocument(item));
        log.debug("ES索引已保存: productId={}", item.getId());
    }

    @Override
    public void saveAll(List<ProductSearchItemDTO> items) {
        if (items == null || items.isEmpty()) {
            return;
        }
        List<ProductSearchDocument> documents = items.stream()
                .filter(m -> m != null && m.getId() != null)
                .map(this::buildDocument)
                .toList();
        if (documents.isEmpty()) {
            return;
        }
        productSearchRepository.saveAll(documents);
        log.debug("ES索引批量保存: {}条", documents.size());
    }

    @Override
    public void delete(Long productId) {
        if (productId == null) {
            return;
        }
        productSearchRepository.deleteById(productId);
        log.debug("ES索引已删除: productId={}", productId);
    }

    private ProductSearchDocument buildDocument(ProductSearchItemDTO item) {
        ProductSearchDocument document = new ProductSearchDocument();
        document.setId(item.getId());
        document.setCategoryId(item.getCategoryId());
        document.setCategoryName(item.getCategoryName());
        document.setCategorySort(item.getCategorySort());
        document.setName(item.getName());
        document.setCoverImage(item.getCoverImage());
        if (item.getPrice() != null) {
            document.setPrice(item.getPrice().multiply(BigDecimal.valueOf(100)).doubleValue());
        }
        document.setStock(item.getStock());
        document.setDescription(item.getDescription());
        document.setStatus(item.getStatus());
        document.setCreatedAt(item.getCreatedAt());
        return document;
    }
}
