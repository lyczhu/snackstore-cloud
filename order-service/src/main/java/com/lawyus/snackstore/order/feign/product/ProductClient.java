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

    @GetMapping("/product/{id}")
    Result<ProductFeignDetailVO> getProductDetail(@PathVariable("id") Long id);

    @GetMapping("product/listByIds")
    Result<List<ProductVO>> getProductsByIds(List<Long> idList);

    @PostMapping("/product/deductStock")
    Result<Boolean> deductStock(@RequestParam("productId") Long productId, @RequestParam("quantity") Integer quantity);

    @PostMapping("/product/rollbackStock")
    Result<Boolean> rollbackStock(@RequestParam("productId") Long productId, @RequestParam("quantity") Integer quantity);

    @PostMapping("/product/batchDeductStock")
    Result<Boolean> batchDeductStock(@RequestBody BatchStockDTO batchDTO);

    @PostMapping("/product/batchRollbackStock")
    Result<Boolean> batchRollbackStock(@RequestBody BatchStockDTO batchDTO);
}
