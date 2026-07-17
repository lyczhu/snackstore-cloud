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
import com.lawyus.snackstore.common.message.ProductSearchSyncMessage;
import com.lawyus.snackstore.common.response.PageResult;
import com.lawyus.snackstore.common.response.Result;
import com.lawyus.snackstore.product.model.vo.ProductCategoryVO;
import com.lawyus.snackstore.product.service.ProductInternalService;

@RestController
@RequestMapping("/product/internal")
public class ProductInternalController {

    private final ProductInternalService productInternalService;

    public ProductInternalController(ProductInternalService productInternalService) {
        this.productInternalService = productInternalService;
    }

    @PostMapping("/searchFallback")
    public PageResult<ProductSearchSyncMessage> searchFallback(@RequestBody @Valid ProductSearchDTO dto) {
        return productInternalService.searchFallback(dto);
    }

    @GetMapping("/listForSearch")
    public PageResult<ProductSearchSyncMessage> listForSearch(@RequestParam int pageNum,
                                                              @RequestParam int pageSize) {
        return productInternalService.listForSearch(pageNum, pageSize);
    }

    @GetMapping("/count")
    public Result<Long> countProducts() {
        return Result.success(productInternalService.countProducts());
    }

    @GetMapping("/categoryMap")
    public Result<Map<Long, Long>> getProductCategoryMap() {
        return Result.success(productInternalService.getProductCategoryMap());
    }

    @GetMapping("/categories")
    public Result<List<ProductCategoryVO>> getCategories() {
        return Result.success(productInternalService.getCategories());
    }
}
