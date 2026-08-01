package com.lawyus.snackstore.product.search.service;

import com.lawyus.snackstore.common.message.ProductSearchSyncMessage;

import java.util.List;

public interface ProductSearchIndexService {

    void save(ProductSearchSyncMessage message);

    void saveAll(List<ProductSearchSyncMessage> messages);

    void delete(Long productId);
}
