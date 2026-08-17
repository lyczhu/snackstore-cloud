package com.lawyus.snackstore.product.search.service;

import com.lawyus.snackstore.common.dto.ProductSearchItemDTO;

import java.util.List;

public interface ProductSearchIndexService {

    void save(ProductSearchItemDTO item);

    void saveAll(List<ProductSearchItemDTO> items);

    void delete(Long productId);
}
