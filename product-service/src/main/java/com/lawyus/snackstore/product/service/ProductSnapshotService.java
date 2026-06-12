package com.lawyus.snackstore.product.service;

import com.lawyus.snackstore.common.response.PageResult;
import com.lawyus.snackstore.product.model.dto.ProductSnapshotQueryDTO;
import com.lawyus.snackstore.product.model.entity.Product;
import com.lawyus.snackstore.product.model.vo.ProductSnapshotVO;

public interface ProductSnapshotService {

    Long createSnapshot(Product product);

    ProductSnapshotVO getSnapshotById(Long id);

    PageResult<ProductSnapshotVO> getSnapshotList(ProductSnapshotQueryDTO queryDTO);
}
