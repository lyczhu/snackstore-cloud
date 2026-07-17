package com.lawyus.snackstore.product.search.service;

import com.lawyus.snackstore.common.dto.ProductSearchDTO;
import com.lawyus.snackstore.common.response.PageResult;
import com.lawyus.snackstore.product.search.model.vo.ProductSearchVO;

public interface ProductSearchService {

    PageResult<ProductSearchVO> search(ProductSearchDTO dto);
}
