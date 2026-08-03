package com.lawyus.snackstore.product.service.impl;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import org.springframework.stereotype.Service;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.lawyus.snackstore.common.dto.ProductSearchDTO;
import com.lawyus.snackstore.common.message.ProductSearchSyncMessage;
import com.lawyus.snackstore.common.response.PageResult;
import com.lawyus.snackstore.product.model.entity.Product;
import com.lawyus.snackstore.product.model.vo.ProductCategoryVO;
import com.lawyus.snackstore.product.model.vo.ProductVO;
import com.lawyus.snackstore.product.repository.ProductMapper;
import com.lawyus.snackstore.product.service.ProductCategoryService;
import com.lawyus.snackstore.product.service.ProductInternalService;
import com.lawyus.snackstore.product.service.ProductService;

@Service
public class ProductInternalServiceImpl implements ProductInternalService {

    private final ProductService productService;
    private final ProductMapper productMapper;
    private final ProductCategoryService categoryService;

    public ProductInternalServiceImpl(ProductService productService,
                                      ProductMapper productMapper,
                                      ProductCategoryService categoryService) {
        this.productService = productService;
        this.productMapper = productMapper;
        this.categoryService = categoryService;
    }

    @Override
    public PageResult<ProductSearchSyncMessage> searchFallback(ProductSearchDTO dto) {
        PageResult<ProductVO> result = productService.searchByKeyword(dto);
        Map<Long, ProductCategoryVO> categoryMap = loadCategoryMap();
        List<ProductSearchSyncMessage> messages = result.getData().stream()
                .map(vo -> convertToMessage(vo, categoryMap.get(vo.getCategoryId())))
                .toList();
        return PageResult.success(messages, result.getPageNum(), result.getPageSize(), result.getTotal());
    }

    @Override
    public PageResult<ProductSearchSyncMessage> listForSearch(int pageNum, int pageSize) {
        pageNum = Math.max(1, pageNum);
        pageSize = Math.min(Math.max(1, pageSize), 200);
        Page<Product> page = new Page<>(pageNum, pageSize);
        Page<Product> result = productMapper.selectPage(page, null);
        Map<Long, ProductCategoryVO> categoryMap = loadCategoryMap();
        List<ProductSearchSyncMessage> messages = result.getRecords().stream()
                .map(p -> convertToMessage(p, categoryMap.get(p.getCategoryId())))
                .toList();
        return PageResult.success(messages, result.getCurrent(), result.getSize(), result.getTotal());
    }

    @Override
    public Long countProducts() {
        return productMapper.countValidProducts();
    }

    @Override
    public Map<Long, Long> getProductCategoryMap() {
        return productMapper.selectList(null).stream()
                .collect(Collectors.toMap(Product::getId, Product::getCategoryId));
    }

    @Override
    public List<ProductCategoryVO> getCategories() {
        return categoryService.getCategoryList();
    }

    private Map<Long, ProductCategoryVO> loadCategoryMap() {
        return categoryService.getCategoryList().stream()
                .collect(Collectors.toMap(ProductCategoryVO::getId, c -> c));
    }

    private ProductSearchSyncMessage convertToMessage(ProductVO vo, ProductCategoryVO category) {
        ProductSearchSyncMessage message = new ProductSearchSyncMessage();
        message.setId(vo.getId());
        message.setCategoryId(vo.getCategoryId());
        message.setCategoryName(vo.getCategoryName());
        message.setCategorySort(vo.getCategorySort());
        message.setName(vo.getName());
        message.setCoverImage(vo.getCoverImage());
        message.setPrice(vo.getPrice());
        message.setStock(vo.getStock());
        message.setDescription(vo.getDescription());
        message.setStatus(vo.getStatus());
        return message;
    }

    private ProductSearchSyncMessage convertToMessage(Product product, ProductCategoryVO category) {
        ProductSearchSyncMessage message = new ProductSearchSyncMessage();
        message.setId(product.getId());
        message.setCategoryId(product.getCategoryId());
        if (category != null) {
            message.setCategoryName(category.getName());
            message.setCategorySort(category.getSort());
        }
        message.setName(product.getName());
        message.setCoverImage(product.getCoverImage());
        message.setPrice(product.getPrice());
        message.setStock(product.getStock());
        message.setDescription(product.getDescription());
        message.setStatus(product.getStatus());
        message.setCreatedAt(product.getCreatedAt());
        return message;
    }
}
