package com.lawyus.snackstore.product.controller;

import com.lawyus.snackstore.common.response.PageResult;
import com.lawyus.snackstore.common.response.Result;
import com.lawyus.snackstore.product.model.dto.BatchStockDTO;
import com.lawyus.snackstore.product.model.dto.ProductDTO;
import com.lawyus.snackstore.product.model.dto.ProductQueryDTO;
import com.lawyus.snackstore.product.model.vo.ProductDetailVO;
import com.lawyus.snackstore.product.model.vo.ProductVO;
import com.lawyus.snackstore.product.service.ProductService;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/products")
public class ProductController {

    private final ProductService productService;

    public ProductController(ProductService productService) {
        this.productService = productService;
    }

    @GetMapping
    public Result<PageResult<ProductVO>> getProducts(@Valid ProductQueryDTO queryDTO) {
        return Result.success(productService.getProductList(queryDTO));
    }

    @GetMapping(value = "/", params = "ids")
    public Result<List<ProductVO>> getProductsByIds(@RequestParam("ids") List<Long> ids) {
        return Result.success(productService.getProductListByIds(ids));
    }

    @GetMapping("/{id}")
    public Result<ProductDetailVO> getProductDetail(@PathVariable Long id) {
        return Result.success(productService.getProductDetail(id));
    }

    @PostMapping
    public Result<ProductVO> createProduct(@Valid @RequestBody ProductDTO dto) {
        return Result.success(productService.createProduct(dto));
    }

    @PutMapping("/{id}")
    public Result<ProductVO> updateProduct(@PathVariable Long id, @Valid @RequestBody ProductDTO dto) {
        return Result.success(productService.updateProduct(id, dto));
    }

    @DeleteMapping("/{id}")
    public Result<Void> deleteProduct(@PathVariable Long id) {
        productService.deleteProduct(id);
        return Result.success(null);
    }

    @PatchMapping("/{id}/status")
    public Result<Void> updateProductStatus(@PathVariable Long id, @RequestParam Integer status) {
        productService.updateProductStatus(id, status);
        return Result.success(null);
    }

    @PostMapping("/{id}/stock/deductions")
    public Result<Boolean> deductStock(@PathVariable("id") Long productId, @RequestParam Integer quantity) {
        return Result.success(productService.deductStock(productId, quantity));
    }

    @PostMapping("/{id}/stock/rollbacks")
    public Result<Boolean> rollbackStock(@PathVariable("id") Long productId, @RequestParam Integer quantity) {
        return Result.success(productService.rollbackStock(productId, quantity));
    }

    @PostMapping("/stock/batch-deductions")
    public Result<Boolean> batchDeductStock(@Valid @RequestBody BatchStockDTO batchDTO) {
        return Result.success(productService.batchDeductStock(batchDTO));
    }

    @PostMapping("/stock/batch-rollbacks")
    public Result<Boolean> batchRollbackStock(@Valid @RequestBody BatchStockDTO batchDTO) {
        return Result.success(productService.batchRollbackStock(batchDTO));
    }
}
