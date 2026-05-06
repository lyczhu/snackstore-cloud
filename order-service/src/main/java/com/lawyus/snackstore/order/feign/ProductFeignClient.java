package com.lawyus.snackstore.order.feign;

import com.lawyus.snackstore.common.response.Result;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;

@FeignClient(name = "product-service")
public interface ProductFeignClient {

    @GetMapping("/product/{id}")
    Result<?> getProductDetail(@PathVariable("id") Long id);

    @PostMapping("/product/deductStock")
    Result<Boolean> deductStock(@RequestParam("productId") Long productId, @RequestParam("quantity") Integer quantity);

    @PostMapping("/product/rollbackStock")
    Result<Boolean> rollbackStock(@RequestParam("productId") Long productId, @RequestParam("quantity") Integer quantity);
}
