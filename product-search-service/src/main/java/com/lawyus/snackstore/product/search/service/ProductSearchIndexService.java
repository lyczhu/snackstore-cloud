package com.lawyus.snackstore.product.search.service;

import com.lawyus.snackstore.common.message.ProductSearchSyncMessage;

public interface ProductSearchIndexService {

    void save(ProductSearchSyncMessage message);

    void delete(Long productId);
}
