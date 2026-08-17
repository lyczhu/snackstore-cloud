package com.lawyus.snackstore.product.service;

import java.util.List;
import java.util.Map;

import com.lawyus.snackstore.common.dto.ProductSearchDTO;
import com.lawyus.snackstore.common.dto.ProductSearchItemDTO;
import com.lawyus.snackstore.common.response.PageResult;
import com.lawyus.snackstore.product.model.vo.ProductCategoryVO;

public interface ProductInternalService {

    PageResult<ProductSearchItemDTO> searchFallback(ProductSearchDTO dto);

    PageResult<ProductSearchItemDTO> listForSearch(int pageNum, int pageSize);

    Long countProducts();

    Map<Long, Long> getProductCategoryMap();

    List<ProductCategoryVO> getCategories();
}
