package com.lawyus.snackstore.product.service;

import com.lawyus.snackstore.common.response.PageResult;
import com.lawyus.snackstore.product.model.dto.ProductDTO;
import com.lawyus.snackstore.product.model.dto.ProductQueryDTO;
import com.lawyus.snackstore.product.model.vo.ProductDetailVO;
import com.lawyus.snackstore.product.model.vo.ProductVO;

public interface ProductService {

    PageResult<ProductVO> getProductList(ProductQueryDTO queryDTO);

    ProductDetailVO getProductDetail(Long id);

    ProductVO createProduct(ProductDTO dto);

    ProductVO updateProduct(Long id, ProductDTO dto);

    void deleteProduct(Long id);

    void updateProductStatus(Long id, Integer status);

    boolean deductStock(Long productId, Integer quantity);

    boolean rollbackStock(Long productId, Integer quantity);
}
