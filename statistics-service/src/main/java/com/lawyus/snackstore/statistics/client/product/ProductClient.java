package com.lawyus.snackstore.statistics.client.product;

import java.util.List;
import java.util.Map;

import com.lawyus.snackstore.common.response.Result;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;

@FeignClient(name = "product-service", fallbackFactory = ProductClientFallbackFactory.class)
public interface ProductClient {

    @GetMapping("/internal/products/count")
    Result<Long> countProducts();

    @GetMapping("/internal/products/category-map")
    Result<Map<Long, Long>> getProductCategoryMap();

    @GetMapping("/internal/products/categories")
    Result<List<ProductCategoryVO>> getCategories();
}
