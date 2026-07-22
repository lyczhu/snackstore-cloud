package com.lawyus.snackstore.statistics.client.product;

import java.util.List;
import java.util.Map;

import com.lawyus.snackstore.common.response.Result;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;

@FeignClient(name = "product-service", fallbackFactory = ProductClientFallbackFactory.class)
public interface ProductClient {

    @GetMapping("/product/internal/count")
    Result<Long> countProducts();

    @GetMapping("/product/internal/categoryMap")
    Result<Map<Long, Long>> getProductCategoryMap();

    @GetMapping("/product/internal/categories")
    Result<List<ProductCategoryVO>> getCategories();
}
