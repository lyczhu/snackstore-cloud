package com.lawyus.snackstore.order.feign.product;

import com.lawyus.snackstore.common.response.Result;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cloud.openfeign.FallbackFactory;

import java.util.List;

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
            public Result<List<ProductVO>> getProductsByIds(List<Long> idList) {
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

            @Override
            public Result<Boolean> batchDeductStock(BatchStockDTO batchDTO) {
                return Result.failed(500, "ProductService unavailable");
            }

            @Override
            public Result<Boolean> batchRollbackStock(BatchStockDTO batchDTO) {
                return Result.failed(500, "ProductService unavailable");
            }
        };
    }
}
