package com.lawyus.snackstore.product.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.lawyus.snackstore.product.exception.BusinessExceptionEnum;
import com.lawyus.snackstore.product.model.entity.Product;
import com.lawyus.snackstore.product.model.event.ProductChangedEvent;
import com.lawyus.snackstore.product.model.event.ProductChangedEvent.ChangeType;
import com.lawyus.snackstore.product.repository.ProductCategoryMapper;
import com.lawyus.snackstore.product.repository.ProductMapper;
import com.lawyus.snackstore.product.model.dto.ProductCategoryDTO;
import com.lawyus.snackstore.product.model.entity.ProductCategory;
import com.lawyus.snackstore.product.model.vo.ProductCategoryVO;
import com.lawyus.snackstore.product.service.ProductCategoryService;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
public class ProductCategoryServiceImpl implements ProductCategoryService {

    private static final String CACHE_NAME = "product:category";

    private final ProductCategoryMapper categoryMapper;
    private final ProductMapper productMapper;
    private final ApplicationEventPublisher eventPublisher;

    public ProductCategoryServiceImpl(ProductCategoryMapper categoryMapper,
                                      ProductMapper productMapper,
                                      ApplicationEventPublisher eventPublisher) {
        this.categoryMapper = categoryMapper;
        this.productMapper = productMapper;
        this.eventPublisher = eventPublisher;
    }

    @Override
    @Cacheable(value = CACHE_NAME, key = "'list'")
    public List<ProductCategoryVO> getCategoryList() {
        List<ProductCategory> categories = categoryMapper.selectList(
                new LambdaQueryWrapper<ProductCategory>()
                        .eq(ProductCategory::getStatus, 1)
                        .orderByAsc(ProductCategory::getSort));
        return categories.stream().map(this::convertToVO).toList();
    }

    @Override
    @Cacheable(value = CACHE_NAME, key = "#id")
    public ProductCategoryVO getCategoryById(Long id) {
        ProductCategory category = categoryMapper.selectById(id);
        if (category == null) {
            throw BusinessExceptionEnum.CATEGORY_NOT_FOUND.getException();
        }
        return convertToVO(category);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    @CacheEvict(value = CACHE_NAME, allEntries = true)
    public ProductCategoryVO createCategory(ProductCategoryDTO dto) {
        Long count = categoryMapper.selectCount(
                new LambdaQueryWrapper<ProductCategory>().eq(ProductCategory::getName, dto.getName()));
        if (count > 0) {
            throw BusinessExceptionEnum.CATEGORY_ALREADY_EXISTS.getException();
        }
        ProductCategory category = new ProductCategory();
        category.setName(dto.getName());
        category.setSort(dto.getSort() != null ? dto.getSort() : 0);
        category.setStatus(dto.getStatus() != null ? dto.getStatus() : 1);
        categoryMapper.insert(category);
        return convertToVO(category);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    @CacheEvict(value = CACHE_NAME, allEntries = true)
    public ProductCategoryVO updateCategory(Long id, ProductCategoryDTO dto) {
        ProductCategory category = categoryMapper.selectById(id);
        if (category == null) {
            throw BusinessExceptionEnum.CATEGORY_NOT_FOUND.getException();
        }
        if (dto.getName() != null) {
            category.setName(dto.getName());
        }
        if (dto.getSort() != null) {
            category.setSort(dto.getSort());
        }
        if (dto.getStatus() != null) {
            category.setStatus(dto.getStatus());
        }
        categoryMapper.updateById(category);
        publishCategoryChangedEvents(id);
        return convertToVO(category);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    @CacheEvict(value = CACHE_NAME, allEntries = true)
    public void deleteCategory(Long id) {
        ProductCategory category = categoryMapper.selectById(id);
        if (category == null) {
            throw BusinessExceptionEnum.CATEGORY_NOT_FOUND.getException();
        }
        Long productCount = productMapper.selectCount(
                new LambdaQueryWrapper<Product>().eq(Product::getCategoryId, id));
        if (productCount > 0) {
            throw BusinessExceptionEnum.CATEGORY_IN_USE.getException();
        }
        categoryMapper.deleteById(id);
    }

    private void publishCategoryChangedEvents(Long categoryId) {
        List<Long> productIds = productMapper.selectList(
                        new LambdaQueryWrapper<Product>().eq(Product::getCategoryId, categoryId)).stream()
                .map(Product::getId)
                .toList();
        if (productIds.isEmpty()) {
            return;
        }
        productIds.forEach(productId ->
                eventPublisher.publishEvent(new ProductChangedEvent(productId, ChangeType.UPDATED)));
    }

    private ProductCategoryVO convertToVO(ProductCategory category) {
        ProductCategoryVO vo = new ProductCategoryVO();
        vo.setId(category.getId());
        vo.setName(category.getName());
        vo.setSort(category.getSort());
        vo.setStatus(category.getStatus());
        return vo;
    }
}
