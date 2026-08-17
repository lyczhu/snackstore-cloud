package com.lawyus.snackstore.product.service.impl;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import org.springframework.stereotype.Service;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.lawyus.snackstore.common.dto.ProductSearchDTO;
import com.lawyus.snackstore.common.dto.ProductSearchItemDTO;
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
    public PageResult<ProductSearchItemDTO> searchFallback(ProductSearchDTO dto) {
        PageResult<ProductVO> result = productService.searchByKeyword(dto);
        Map<Long, ProductCategoryVO> categoryMap = loadCategoryMap();
        List<ProductSearchItemDTO> items = result.getData().stream()
                .map(vo -> convertToItem(vo, categoryMap.get(vo.getCategoryId())))
                .toList();
        return PageResult.success(items, result.getPageNum(), result.getPageSize(), result.getTotal());
    }

    @Override
    public PageResult<ProductSearchItemDTO> listForSearch(int pageNum, int pageSize) {
        pageNum = Math.max(1, pageNum);
        pageSize = Math.min(Math.max(1, pageSize), 200);
        Page<Product> page = new Page<>(pageNum, pageSize);
        Page<Product> result = productMapper.selectPage(page, null);
        Map<Long, ProductCategoryVO> categoryMap = loadCategoryMap();
        List<ProductSearchItemDTO> items = result.getRecords().stream()
                .map(p -> convertToItem(p, categoryMap.get(p.getCategoryId())))
                .toList();
        return PageResult.success(items, result.getCurrent(), result.getSize(), result.getTotal());
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

    private ProductSearchItemDTO convertToItem(ProductVO vo, ProductCategoryVO category) {
        ProductSearchItemDTO item = new ProductSearchItemDTO();
        item.setId(vo.getId());
        item.setCategoryId(vo.getCategoryId());
        item.setCategoryName(vo.getCategoryName());
        item.setCategorySort(vo.getCategorySort());
        item.setName(vo.getName());
        item.setCoverImage(vo.getCoverImage());
        item.setPrice(vo.getPrice());
        item.setStock(vo.getStock());
        item.setDescription(vo.getDescription());
        item.setStatus(vo.getStatus());
        return item;
    }

    private ProductSearchItemDTO convertToItem(Product product, ProductCategoryVO category) {
        ProductSearchItemDTO item = new ProductSearchItemDTO();
        item.setId(product.getId());
        item.setCategoryId(product.getCategoryId());
        if (category != null) {
            item.setCategoryName(category.getName());
            item.setCategorySort(category.getSort());
        }
        item.setName(product.getName());
        item.setCoverImage(product.getCoverImage());
        item.setPrice(product.getPrice());
        item.setStock(product.getStock());
        item.setDescription(product.getDescription());
        item.setStatus(product.getStatus());
        item.setCreatedAt(product.getCreatedAt());
        return item;
    }
}
