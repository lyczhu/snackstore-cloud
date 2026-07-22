package com.lawyus.snackstore.statistics.client.product;

import java.util.List;
import java.util.Map;

import com.lawyus.snackstore.common.response.Result;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cloud.openfeign.FallbackFactory;
import org.springframework.stereotype.Component;

@Slf4j
@Component
public class ProductClientFallbackFactory implements FallbackFactory<ProductClient> {

    @Override
    public ProductClient create(Throwable cause) {
        log.error("ProductClient fallback triggered: {}", cause.getMessage(), cause);
        return new ProductClient() {
            @Override
            public Result<Long> countProducts() {
                return Result.failed(500, "ProductService unavailable");
            }

            @Override
            public Result<Map<Long, Long>> getProductCategoryMap() {
                return Result.failed(500, "ProductService unavailable");
            }

            @Override
            public Result<List<ProductCategoryVO>> getCategories() {
                return Result.failed(500, "ProductService unavailable");
            }
        };
    }
}
