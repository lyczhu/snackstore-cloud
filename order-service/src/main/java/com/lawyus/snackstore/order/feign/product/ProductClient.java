package com.lawyus.snackstore.order.feign.product;

import com.lawyus.snackstore.common.response.Result;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;

import java.util.List;

@FeignClient(name = "product-service", fallbackFactory = ProductClientFallbackFactory.class)
public interface ProductClient {

    @GetMapping("/products/{id}")
    Result<ProductFeignDetailVO> getProductDetail(@PathVariable("id") Long id);

    @GetMapping("/products/")
    Result<List<ProductVO>> getProductsByIds(@RequestParam("ids") List<Long> ids);

    @PostMapping("/products/{id}/stock/deductions")
    Result<Boolean> deductStock(@PathVariable("id") Long productId, @RequestParam("quantity") Integer quantity);

    @PostMapping("/products/{id}/stock/rollbacks")
    Result<Boolean> rollbackStock(@PathVariable("id") Long productId, @RequestParam("quantity") Integer quantity);

    @PostMapping("/products/stock/batch-deductions")
    Result<Boolean> batchDeductStock(@RequestBody BatchStockDTO batchDTO);

    @PostMapping("/products/stock/batch-rollbacks")
    Result<Boolean> batchRollbackStock(@RequestBody BatchStockDTO batchDTO);
}
