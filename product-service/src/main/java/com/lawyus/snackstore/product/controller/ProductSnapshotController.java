package com.lawyus.snackstore.product.controller;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.lawyus.snackstore.common.response.PageResult;
import com.lawyus.snackstore.common.response.Result;
import com.lawyus.snackstore.product.model.dto.ProductSnapshotQueryDTO;
import com.lawyus.snackstore.product.model.vo.ProductSnapshotVO;
import com.lawyus.snackstore.product.service.ProductSnapshotService;

import jakarta.validation.Valid;

@RestController
@RequestMapping("/products/snapshots")
public class ProductSnapshotController {

    private final ProductSnapshotService snapshotService;

    public ProductSnapshotController(ProductSnapshotService snapshotService) {
        this.snapshotService = snapshotService;
    }

    @GetMapping("/{id}")
    public Result<ProductSnapshotVO> getSnapshotById(@PathVariable Long id) {
        return Result.success(snapshotService.getSnapshotById(id));
    }

    @GetMapping
    public Result<PageResult<ProductSnapshotVO>> getSnapshotList(@Valid ProductSnapshotQueryDTO queryDTO) {
        return Result.success(snapshotService.getSnapshotList(queryDTO));
    }
}
