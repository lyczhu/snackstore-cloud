package com.lawyus.snackstore.product.search.controller;

import com.lawyus.snackstore.common.dto.ProductSearchDTO;
import com.lawyus.snackstore.common.response.PageResult;
import com.lawyus.snackstore.common.response.Result;
import com.lawyus.snackstore.product.search.model.vo.ProductSearchVO;
import com.lawyus.snackstore.product.search.service.ProductSearchService;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/product/search")
public class ProductSearchController {

    private final ProductSearchService productSearchService;

    public ProductSearchController(ProductSearchService productSearchService) {
        this.productSearchService = productSearchService;
    }

    @GetMapping
    public Result<PageResult<ProductSearchVO>> searchProduct(@Valid ProductSearchDTO searchDTO) {
        return Result.success(productSearchService.search(searchDTO));
    }
}