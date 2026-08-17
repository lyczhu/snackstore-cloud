package com.lawyus.snackstore.product.search.client;

import com.lawyus.snackstore.common.dto.ProductSearchDTO;
import com.lawyus.snackstore.common.dto.ProductSearchItemDTO;
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
    PageResult<ProductSearchItemDTO> searchFallback(@RequestBody @Valid ProductSearchDTO dto);

    @GetMapping("/internal/products")
    PageResult<ProductSearchItemDTO> listForSearch(@RequestParam("pageNum") int pageNum,
                                                   @RequestParam("pageSize") int pageSize);
}
