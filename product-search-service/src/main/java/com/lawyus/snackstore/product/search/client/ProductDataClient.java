package com.lawyus.snackstore.product.search.client;

import com.lawyus.snackstore.common.dto.ProductSearchDTO;
import com.lawyus.snackstore.common.message.ProductSearchSyncMessage;
import com.lawyus.snackstore.common.response.PageResult;
import jakarta.validation.Valid;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;

@FeignClient(name = "product-service")
public interface ProductDataClient {

    @PostMapping("/internal/products/search/fallback")
    PageResult<ProductSearchSyncMessage> searchFallback(@RequestBody @Valid ProductSearchDTO dto);

    @GetMapping("/internal/products")
    PageResult<ProductSearchSyncMessage> listForSearch(@RequestParam(value = "purpose", required = false) String purpose,
                                                       @RequestParam("pageNum") int pageNum,
                                                       @RequestParam("pageSize") int pageSize);
}
