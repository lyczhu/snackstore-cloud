package com.lawyus.snackstore.product.controller;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.lawyus.snackstore.common.constant.AuthConstants;
import com.lawyus.snackstore.common.response.PageResult;
import com.lawyus.snackstore.common.response.Result;
import com.lawyus.snackstore.product.exception.BusinessExceptionEnum;
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
    public Result<ProductSnapshotVO> getSnapshotById(
            @PathVariable Long id,
            @RequestHeader(value = AuthConstants.HEADER_USER_ROLE, required = false) String role) {
        assertAdmin(role);
        return Result.success(snapshotService.getSnapshotById(id));
    }

    @GetMapping
    public Result<PageResult<ProductSnapshotVO>> getSnapshotList(
            @Valid ProductSnapshotQueryDTO queryDTO,
            @RequestHeader(value = AuthConstants.HEADER_USER_ROLE, required = false) String role) {
        assertAdmin(role);
        return Result.success(snapshotService.getSnapshotList(queryDTO));
    }

    /**
     * 商品变更快照属管理数据（决策 D8）：网关已按 admin-only 拦截，
     * 这里是第二层断言——下游服务可被内网直连，不能只靠网关。
     */
    private void assertAdmin(String role) {
        if (!AuthConstants.ROLE_ADMIN.equalsIgnoreCase(role)) {
            throw BusinessExceptionEnum.ACCESS_FORBIDDEN.getException("仅管理员可查看商品变更快照");
        }
    }
}
