package com.lawyus.snackstore.product.service;

import com.lawyus.snackstore.common.response.PageResult;
import com.lawyus.snackstore.product.model.dto.ProductCategoryDTO;
import com.lawyus.snackstore.product.model.vo.ProductCategoryVO;

import java.util.List;

public interface ProductCategoryService {

    List<ProductCategoryVO> getCategoryList();

    ProductCategoryVO getCategoryById(Long id);

    ProductCategoryVO createCategory(ProductCategoryDTO dto);

    ProductCategoryVO updateCategory(Long id, ProductCategoryDTO dto);

    void deleteCategory(Long id);
}
