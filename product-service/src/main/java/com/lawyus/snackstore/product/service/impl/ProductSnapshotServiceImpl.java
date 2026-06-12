package com.lawyus.snackstore.product.service.impl;

import org.springframework.stereotype.Service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.lawyus.snackstore.common.exception.BusinessExceptionEnum;
import com.lawyus.snackstore.common.response.PageResult;
import com.lawyus.snackstore.product.mapper.ProductCategoryMapper;
import com.lawyus.snackstore.product.mapper.ProductSnapshotMapper;
import com.lawyus.snackstore.product.model.dto.ProductSnapshotQueryDTO;
import com.lawyus.snackstore.product.model.entity.Product;
import com.lawyus.snackstore.product.model.entity.ProductCategory;
import com.lawyus.snackstore.product.model.entity.ProductSnapshot;
import com.lawyus.snackstore.product.model.vo.ProductSnapshotVO;
import com.lawyus.snackstore.product.service.ProductSnapshotService;

@Service
public class ProductSnapshotServiceImpl implements ProductSnapshotService {

    private final ProductSnapshotMapper snapshotMapper;
    private final ProductCategoryMapper categoryMapper;

    public ProductSnapshotServiceImpl(ProductSnapshotMapper snapshotMapper, ProductCategoryMapper categoryMapper) {
        this.snapshotMapper = snapshotMapper;
        this.categoryMapper = categoryMapper;
    }

    @Override
    public Long createSnapshot(Product product) {
        ProductSnapshot snapshot = new ProductSnapshot();
        snapshot.setProductId(product.getId());
        snapshot.setCategoryId(product.getCategoryId());
        if (product.getCategoryId() != null) {
            ProductCategory category = categoryMapper.selectById(product.getCategoryId());
            if (category != null) {
                snapshot.setCategoryName(category.getName());
                snapshot.setCategorySort(category.getSort());
            }
        }
        snapshot.setName(product.getName());
        snapshot.setCoverImage(product.getCoverImage());
        snapshot.setPrice(product.getPrice());
        snapshot.setStock(product.getStock());
        snapshot.setDescription(product.getDescription());
        snapshot.setDetail(product.getDetail());
        snapshot.setStatus(product.getStatus());
        snapshotMapper.insert(snapshot);
        return snapshot.getId();
    }

    @Override
    public ProductSnapshotVO getSnapshotById(Long id) {
        ProductSnapshot snapshot = snapshotMapper.selectById(id);
        if (snapshot == null) {
            throw BusinessExceptionEnum.SNAPSHOT_NOT_FOUND.getException();
        }
        return convertToVO(snapshot);
    }

    @Override
    public PageResult<ProductSnapshotVO> getSnapshotList(ProductSnapshotQueryDTO queryDTO) {
        LambdaQueryWrapper<ProductSnapshot> wrapper = new LambdaQueryWrapper<>();
        if (queryDTO.getProductId() != null) {
            wrapper.eq(ProductSnapshot::getProductId, queryDTO.getProductId());
        }
        wrapper.orderByDesc(ProductSnapshot::getCreatedAt);

        Page<ProductSnapshot> page = new Page<>(queryDTO.getPageNum(), queryDTO.getPageSize());
        Page<ProductSnapshot> result = snapshotMapper.selectPage(page, wrapper);

        return PageResult.success(
                result.getRecords().stream().map(this::convertToVO).toList(),
                result.getCurrent(), result.getSize(), result.getTotal());
    }

    private ProductSnapshotVO convertToVO(ProductSnapshot snapshot) {
        ProductSnapshotVO vo = new ProductSnapshotVO();
        vo.setId(snapshot.getId());
        vo.setProductId(snapshot.getProductId());
        vo.setCategoryId(snapshot.getCategoryId());
        vo.setCategoryName(snapshot.getCategoryName());
        vo.setCategorySort(snapshot.getCategorySort());
        vo.setName(snapshot.getName());
        vo.setCoverImage(snapshot.getCoverImage());
        vo.setPrice(snapshot.getPrice());
        vo.setStock(snapshot.getStock());
        vo.setDescription(snapshot.getDescription());
        vo.setDetail(snapshot.getDetail());
        vo.setStatus(snapshot.getStatus());
        vo.setCreatedAt(snapshot.getCreatedAt());
        return vo;
    }
}
