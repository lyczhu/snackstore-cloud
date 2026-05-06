package com.lawyus.snackstore.product.controller;

import com.lawyus.snackstore.common.response.Result;
import com.lawyus.snackstore.product.model.dto.ProductCategoryDTO;
import com.lawyus.snackstore.product.model.vo.ProductCategoryVO;
import com.lawyus.snackstore.product.service.ProductCategoryService;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/product/category")
public class ProductCategoryController {

    private final ProductCategoryService categoryService;

    public ProductCategoryController(ProductCategoryService categoryService) {
        this.categoryService = categoryService;
    }

    @GetMapping("/list")
    public Result<List<ProductCategoryVO>> getCategoryList() {
        return Result.success(categoryService.getCategoryList());
    }

    @GetMapping("/{id}")
    public Result<ProductCategoryVO> getCategoryById(@PathVariable Long id) {
        return Result.success(categoryService.getCategoryById(id));
    }

    @PostMapping
    public Result<ProductCategoryVO> createCategory(@Valid @RequestBody ProductCategoryDTO dto) {
        return Result.success(categoryService.createCategory(dto));
    }

    @PutMapping("/{id}")
    public Result<ProductCategoryVO> updateCategory(@PathVariable Long id, @Valid @RequestBody ProductCategoryDTO dto) {
        return Result.success(categoryService.updateCategory(id, dto));
    }

    @DeleteMapping("/{id}")
    public Result<Void> deleteCategory(@PathVariable Long id) {
        categoryService.deleteCategory(id);
        return Result.success(null);
    }
}
