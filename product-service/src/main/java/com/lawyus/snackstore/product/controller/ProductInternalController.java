package com.lawyus.snackstore.product.controller;

import java.util.List;
import java.util.Map;

import jakarta.validation.Valid;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.lawyus.snackstore.common.dto.ProductSearchDTO;
import com.lawyus.snackstore.common.dto.ProductSearchItemDTO;
import com.lawyus.snackstore.common.response.PageResult;
import com.lawyus.snackstore.common.response.Result;
import com.lawyus.snackstore.product.model.vo.ProductCategoryVO;
import com.lawyus.snackstore.product.service.ProductInternalService;

@RestController
@RequestMapping("/internal/products")
public class ProductInternalController {

    private final ProductInternalService productInternalService;

    public ProductInternalController(ProductInternalService productInternalService) {
        this.productInternalService = productInternalService;
    }

    @PostMapping("/search/fallback")
    public PageResult<ProductSearchItemDTO> searchFallback(@RequestBody @Valid ProductSearchDTO dto) {
        return productInternalService.searchFallback(dto);
    }

    @GetMapping
    public PageResult<ProductSearchItemDTO> listForSearch(@RequestParam int pageNum,
                                                          @RequestParam int pageSize) {
        return productInternalService.listForSearch(pageNum, pageSize);
    }

    @GetMapping("/count")
    public Result<Long> countProducts() {
        return Result.success(productInternalService.countProducts());
    }

    @GetMapping("/category-map")
    public Result<Map<Long, Long>> getProductCategoryMap() {
        return Result.success(productInternalService.getProductCategoryMap());
    }

    @GetMapping("/categories")
    public Result<List<ProductCategoryVO>> getCategories() {
        return Result.success(productInternalService.getCategories());
    }
}
