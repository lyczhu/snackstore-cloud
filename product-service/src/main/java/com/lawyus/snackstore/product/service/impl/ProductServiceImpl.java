package com.lawyus.snackstore.product.service.impl;

import java.util.Map;
import java.util.stream.Collectors;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.lawyus.snackstore.common.exception.BusinessExceptionEnum;
import com.lawyus.snackstore.common.response.PageResult;
import com.lawyus.snackstore.product.mapper.ProductCategoryMapper;
import com.lawyus.snackstore.product.mapper.ProductMapper;
import com.lawyus.snackstore.product.model.dto.ProductDTO;
import com.lawyus.snackstore.product.model.dto.ProductQueryDTO;
import com.lawyus.snackstore.product.model.entity.Product;
import com.lawyus.snackstore.product.model.entity.ProductCategory;
import com.lawyus.snackstore.product.model.vo.ProductDetailVO;
import com.lawyus.snackstore.product.model.vo.ProductVO;
import com.lawyus.snackstore.product.service.ProductService;
import com.lawyus.snackstore.product.service.ProductSnapshotService;

@Service
public class ProductServiceImpl implements ProductService {

    private final ProductMapper productMapper;
    private final ProductCategoryMapper categoryMapper;
    private final ProductSnapshotService snapshotService;

    public ProductServiceImpl(ProductMapper productMapper, ProductCategoryMapper categoryMapper, ProductSnapshotService snapshotService) {
        this.productMapper = productMapper;
        this.categoryMapper = categoryMapper;
        this.snapshotService = snapshotService;
    }

    @Override
    public PageResult<ProductVO> getProductList(ProductQueryDTO queryDTO) {
        Map<Long, ProductCategory> categoryMap = categoryMapper.selectList(null).stream()
                .collect(Collectors.toMap(ProductCategory::getId, c -> c));

        LambdaQueryWrapper<Product> wrapper = new LambdaQueryWrapper<>();
        if (queryDTO.getCategoryId() != null) {
            wrapper.eq(Product::getCategoryId, queryDTO.getCategoryId());
        }
        if (queryDTO.getKeyword() != null && !queryDTO.getKeyword().isEmpty()) {
            wrapper.like(Product::getName, queryDTO.getKeyword());
        }
        if (queryDTO.getStatus() != null) {
            wrapper.eq(Product::getStatus, queryDTO.getStatus());
        }
        wrapper.orderByDesc(Product::getCreatedAt);

        Page<Product> page = new Page<>(queryDTO.getPageNum(), queryDTO.getPageSize());
        Page<Product> result = productMapper.selectPage(page, wrapper);

        return PageResult.success(
                result.getRecords().stream().map(p -> convertToVO(p, categoryMap)).toList(),
                result.getCurrent(), result.getSize(), result.getTotal());
    }

    @Override
    public ProductDetailVO getProductDetail(Long id) {
        Product product = productMapper.selectById(id);
        if (product == null) {
            throw BusinessExceptionEnum.PRODUCT_NOT_FOUND.getException();
        }
        return convertToDetailVO(product);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public ProductVO createProduct(ProductDTO dto) {
        Product product = new Product();
        product.setCategoryId(dto.getCategoryId());
        product.setName(dto.getName());
        product.setCoverImage(dto.getCoverImage());
        product.setPrice(dto.getPrice());
        product.setStock(dto.getStock() != null ? dto.getStock() : 0);
        product.setDescription(dto.getDescription());
        product.setDetail(dto.getDetail());
        product.setStatus(dto.getStatus() != null ? dto.getStatus() : 1);
        productMapper.insert(product);
        snapshotService.createSnapshot(product);
        return convertToVO(product, getCategoryMap());
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public ProductVO updateProduct(Long id, ProductDTO dto) {
        Product product = productMapper.selectById(id);
        if (product == null) {
            throw BusinessExceptionEnum.PRODUCT_NOT_FOUND.getException();
        }
        snapshotService.createSnapshot(product);
        if (dto.getCategoryId() != null) {
            product.setCategoryId(dto.getCategoryId());
        }
        if (dto.getName() != null) {
            product.setName(dto.getName());
        }
        if (dto.getCoverImage() != null) {
            product.setCoverImage(dto.getCoverImage());
        }
        if (dto.getPrice() != null) {
            product.setPrice(dto.getPrice());
        }
        if (dto.getStock() != null) {
            product.setStock(dto.getStock());
        }
        if (dto.getDescription() != null) {
            product.setDescription(dto.getDescription());
        }
        if (dto.getDetail() != null) {
            product.setDetail(dto.getDetail());
        }
        if (dto.getStatus() != null) {
            product.setStatus(dto.getStatus());
        }
        productMapper.updateById(product);
        return convertToVO(product, getCategoryMap());
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void deleteProduct(Long id) {
        Product product = productMapper.selectById(id);
        if (product == null) {
            throw BusinessExceptionEnum.PRODUCT_NOT_FOUND.getException();
        }
        snapshotService.createSnapshot(product);
        productMapper.deleteById(id);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void updateProductStatus(Long id, Integer status) {
        Product product = productMapper.selectById(id);
        if (product == null) {
            throw BusinessExceptionEnum.PRODUCT_NOT_FOUND.getException();
        }
        snapshotService.createSnapshot(product);
        product.setStatus(status);
        productMapper.updateById(product);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public boolean deductStock(Long productId, Integer quantity) {
        int rows = productMapper.update(null,
                new LambdaUpdateWrapper<Product>()
                        .eq(Product::getId, productId)
                        .ge(Product::getStock, quantity)
                        .setSql("stock = stock - " + quantity));
        return rows > 0;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public boolean rollbackStock(Long productId, Integer quantity) {
        productMapper.update(null,
                new LambdaUpdateWrapper<Product>()
                        .eq(Product::getId, productId)
                        .setSql("stock = stock + " + quantity));
        return true;
    }

    private Map<Long, ProductCategory> getCategoryMap() {
        return categoryMapper.selectList(null).stream()
                .collect(Collectors.toMap(ProductCategory::getId, c -> c));
    }

    private ProductVO convertToVO(Product product, Map<Long, ProductCategory> categoryMap) {
        ProductVO vo = new ProductVO();
        vo.setId(product.getId());
        vo.setCategoryId(product.getCategoryId());
        if (product.getCategoryId() != null) {
            ProductCategory category = categoryMap.get(product.getCategoryId());
            if (category != null) {
                vo.setCategoryName(category.getName());
                vo.setCategorySort(category.getSort());
            }
        }
        vo.setName(product.getName());
        vo.setCoverImage(product.getCoverImage());
        vo.setPrice(product.getPrice());
        vo.setStock(product.getStock());
        vo.setDescription(product.getDescription());
        vo.setStatus(product.getStatus());
        return vo;
    }

    private ProductDetailVO convertToDetailVO(Product product) {
        Map<Long, ProductCategory> categoryMap = getCategoryMap();
        ProductDetailVO vo = new ProductDetailVO();
        vo.setId(product.getId());
        vo.setCategoryId(product.getCategoryId());
        if (product.getCategoryId() != null) {
            ProductCategory category = categoryMap.get(product.getCategoryId());
            if (category != null) {
                vo.setCategoryName(category.getName());
                vo.setCategorySort(category.getSort());
            }
        }
        vo.setName(product.getName());
        vo.setCoverImage(product.getCoverImage());
        vo.setPrice(product.getPrice());
        vo.setStock(product.getStock());
        vo.setDescription(product.getDescription());
        vo.setDetail(product.getDetail());
        vo.setStatus(product.getStatus());
        return vo;
    }
}
