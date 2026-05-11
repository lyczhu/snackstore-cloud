package com.lawyus.snackstore.order.feign.product;

import com.lawyus.snackstore.common.response.Result;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cloud.openfeign.FallbackFactory;

@Slf4j
public class ProductClientFallbackFactory implements FallbackFactory<ProductClient> {
    @Override
    public ProductClient create(Throwable cause) {
        log.error("ProductClient fallback triggered: {}", cause.getMessage(), cause);
        return new ProductClient() {
            @Override
            public Result<ProductFeignDetailVO> getProductDetail(Long id) {
                return Result.failed(500, "ProductService unavailable");
            }

            @Override
            public Result<Boolean> deductStock(Long productId, Integer quantity) {
                return Result.failed(500, "ProductService unavailable");
            }

            @Override
            public Result<Boolean> rollbackStock(Long productId, Integer quantity) {
                return Result.failed(500, "ProductService unavailable");
            }
        };
    }
}
