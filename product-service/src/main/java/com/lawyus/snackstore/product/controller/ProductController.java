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
@RequestMapping("/product")
public class ProductController {

    private final ProductService productService;

    public ProductController(ProductService productService) {
        this.productService = productService;
    }

    @GetMapping("/list")
    public Result<PageResult<ProductVO>> getProducts(ProductQueryDTO queryDTO) {
        return Result.success(productService.getProductList(queryDTO));
    }

    @PostMapping("/listByIds")
    public Result<List<ProductVO>> getProductsByIds(@RequestBody List<Long> idList) {
        return Result.success(productService.getProductListByIds(idList));
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

    @PutMapping("/{id}/status")
    public Result<Void> updateProductStatus(@PathVariable Long id, @RequestParam Integer status) {
        productService.updateProductStatus(id, status);
        return Result.success(null);
    }

    @PostMapping("/deductStock")
    public Result<Boolean> deductStock(@RequestParam Long productId, @RequestParam Integer quantity) {
        return Result.success(productService.deductStock(productId, quantity));
    }

    @PostMapping("/rollbackStock")
    public Result<Boolean> rollbackStock(@RequestParam Long productId, @RequestParam Integer quantity) {
        return Result.success(productService.rollbackStock(productId, quantity));
    }

    @PostMapping("/batchDeductStock")
    public Result<Boolean> batchDeductStock(@Valid @RequestBody BatchStockDTO batchDTO) {
        return Result.success(productService.batchDeductStock(batchDTO));
    }

    @PostMapping("/batchRollbackStock")
    public Result<Boolean> batchRollbackStock(@Valid @RequestBody BatchStockDTO batchDTO) {
        return Result.success(productService.batchRollbackStock(batchDTO));
    }
}
