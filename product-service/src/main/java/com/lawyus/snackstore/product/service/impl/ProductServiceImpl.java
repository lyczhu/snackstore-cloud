package com.lawyus.snackstore.product.service.impl;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.lawyus.snackstore.common.dto.ProductSearchDTO;
import com.lawyus.snackstore.common.response.PageResult;
import com.lawyus.snackstore.product.exception.BusinessExceptionEnum;
import com.lawyus.snackstore.product.model.dto.BatchStockDTO;
import com.lawyus.snackstore.product.model.dto.ProductDTO;
import com.lawyus.snackstore.product.model.dto.ProductQueryDTO;
import com.lawyus.snackstore.product.model.dto.StockDTO;
import com.lawyus.snackstore.product.model.entity.Product;
import com.lawyus.snackstore.product.model.event.ProductChangedEvent;
import com.lawyus.snackstore.product.model.event.ProductChangedEvent.ChangeType;
import com.lawyus.snackstore.product.model.vo.ProductCategoryVO;
import com.lawyus.snackstore.product.model.vo.ProductDetailVO;
import com.lawyus.snackstore.product.model.vo.ProductVO;
import com.lawyus.snackstore.product.repository.ProductMapper;
import com.lawyus.snackstore.product.service.ProductCategoryService;
import com.lawyus.snackstore.product.service.ProductService;
import com.lawyus.snackstore.product.service.ProductSnapshotService;

import lombok.extern.slf4j.Slf4j;

@Slf4j
@Service
public class ProductServiceImpl implements ProductService {

    private final ProductMapper productMapper;
    private final ProductCategoryService categoryService;
    private final ProductSnapshotService snapshotService;
    private final ApplicationEventPublisher eventPublisher;

    public ProductServiceImpl(ProductMapper productMapper, ProductCategoryService categoryService,
                              ProductSnapshotService snapshotService,
                              ApplicationEventPublisher eventPublisher) {
        this.productMapper = productMapper;
        this.categoryService = categoryService;
        this.snapshotService = snapshotService;
        this.eventPublisher = eventPublisher;
    }

    @Override
    public PageResult<ProductVO> getProductList(ProductQueryDTO queryDTO) {
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

        Map<Long, ProductCategoryVO> categoryMap = getCategoryMap();
        return PageResult.success(
                result.getRecords().stream().map(p -> convertToVO(p, categoryMap)).toList(),
                result.getCurrent(), result.getSize(), result.getTotal());
    }

    @Override
    public PageResult<ProductVO> searchByKeyword(ProductSearchDTO searchDTO) {
        ProductQueryDTO queryDTO = new ProductQueryDTO();
        queryDTO.setKeyword(searchDTO.getKeyword());
        queryDTO.setCategoryId(searchDTO.getCategoryId());
        queryDTO.setStatus(searchDTO.getStatus());
        queryDTO.setPageNum(searchDTO.getPageNum());
        queryDTO.setPageSize(searchDTO.getPageSize());
        log.info("ES搜索降级到MySQL查询: keyword={}", searchDTO.getKeyword());
        return getProductList(queryDTO);
    }

    @Override
    public List<ProductVO> getProductListByIds(List<Long> idList) {
        List<Product> products = productMapper.selectByIds(idList);
        Map<Long, ProductCategoryVO> categoryMap = getCategoryMap();
        return products.stream().map(p -> convertToVO(p, categoryMap)).toList();
    }

    @Override
    public ProductDetailVO getProductDetail(Long id) {
        Product product = getProductEntity(id);
        Map<Long, ProductCategoryVO> categoryMap = getCategoryMap();
        return convertToDetailVO(product, categoryMap);
    }

    @Override
    public Product getProductEntity(Long id) {
        Product product = productMapper.selectById(id);
        if (product == null) {
            throw BusinessExceptionEnum.PRODUCT_NOT_FOUND.getException();
        }
        return product;
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
        eventPublisher.publishEvent(new ProductChangedEvent(product.getId(), ChangeType.CREATED));
        Map<Long, ProductCategoryVO> categoryMap = getCategoryMap();
        return convertToVO(product, categoryMap);
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
        eventPublisher.publishEvent(new ProductChangedEvent(product.getId(), ChangeType.UPDATED));
        Map<Long, ProductCategoryVO> categoryMap = getCategoryMap();
        return convertToVO(product, categoryMap);
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
        eventPublisher.publishEvent(new ProductChangedEvent(id, ChangeType.DELETED));
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
        eventPublisher.publishEvent(new ProductChangedEvent(product.getId(), ChangeType.STATUS_CHANGED));
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public boolean deductStock(Long productId, Integer quantity) {
        int rows = productMapper.update(null,
                new LambdaUpdateWrapper<Product>()
                        .eq(Product::getId, productId)
                        .ge(Product::getStock, quantity)
                        .setSql("stock = stock - " + quantity));
        if (rows > 0) {
            eventPublisher.publishEvent(new ProductChangedEvent(productId, ChangeType.STOCK_CHANGED));
        }
        return rows > 0;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public boolean rollbackStock(Long productId, Integer quantity) {
        productMapper.update(null,
                new LambdaUpdateWrapper<Product>()
                        .eq(Product::getId, productId)
                        .setSql("stock = stock + " + quantity));
        eventPublisher.publishEvent(new ProductChangedEvent(productId, ChangeType.STOCK_CHANGED));
        return true;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public boolean batchDeductStock(BatchStockDTO batchDTO) {
        List<StockDTO> items = batchDTO.getItems();
        log.info("开始批量扣减库存, 订单号: {}, 商品数: {}", batchDTO.getOrderNo(), items.size());

        Set<Long> productIds = items.stream().map(StockDTO::getProductId).collect(Collectors.toSet());
        List<Product> products = productMapper.selectByIds(productIds);
        Map<Long, Product> productMap = products.stream()
                .collect(Collectors.toMap(Product::getId, Function.identity()));

        for (StockDTO item : items) {
            Product product = productMap.get(item.getProductId());
            if (product == null) {
                throw BusinessExceptionEnum.PRODUCT_NOT_FOUND
                        .getException("商品 " + item.getProductId() + " 不存在");
            }
            if (product.getStock() < item.getQuantity()) {
                log.warn("批量扣减库存失败(预检查), 订单号: {}, 商品ID: {}, 库存: {}, 需扣: {}",
                        batchDTO.getOrderNo(), item.getProductId(), product.getStock(), item.getQuantity());
                throw BusinessExceptionEnum.STOCK_NOT_ENOUGH
                        .getException("商品 " + item.getProductId() + " 库存不足");
            }
        }

        for (StockDTO item : items) {
            int rows = productMapper.update(null,
                    new LambdaUpdateWrapper<Product>()
                            .eq(Product::getId, item.getProductId())
                            .ge(Product::getStock, item.getQuantity())
                            .setSql("stock = stock - " + item.getQuantity()));
            if (rows == 0) {
                log.warn("批量扣减库存失败(并发冲突), 订单号: {}, 商品ID: {}",
                        batchDTO.getOrderNo(), item.getProductId());
                throw BusinessExceptionEnum.STOCK_NOT_ENOUGH
                        .getException("商品 " + item.getProductId() + " 库存不足，请重试");
            }
            log.debug("扣减库存成功, 商品ID: {}, 数量: {}", item.getProductId(), item.getQuantity());
        }

        for (StockDTO item : items) {
            eventPublisher.publishEvent(new ProductChangedEvent(item.getProductId(), ChangeType.STOCK_CHANGED));
        }
        log.info("批量扣减库存完成, 订单号: {}", batchDTO.getOrderNo());
        return true;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public boolean batchRollbackStock(BatchStockDTO batchDTO) {
        List<StockDTO> items = batchDTO.getItems();
        log.info("开始批量回滚库存, 订单号: {}, 商品数: {}", batchDTO.getOrderNo(), items.size());

        for (StockDTO item : items) {
            productMapper.update(null,
                    new LambdaUpdateWrapper<Product>()
                            .eq(Product::getId, item.getProductId())
                            .setSql("stock = stock + " + item.getQuantity()));
            log.debug("回滚库存成功, 商品ID: {}, 数量: {}", item.getProductId(), item.getQuantity());
        }

        for (StockDTO item : items) {
            eventPublisher.publishEvent(new ProductChangedEvent(item.getProductId(), ChangeType.STOCK_CHANGED));
        }
        log.info("批量回滚库存完成, 订单号: {}", batchDTO.getOrderNo());
        return true;
    }

    private Map<Long, ProductCategoryVO> getCategoryMap() {
        return categoryService.getCategoryList().stream()
                .collect(Collectors.toMap(ProductCategoryVO::getId, c -> c));
    }

    private ProductVO convertToVO(Product product, Map<Long, ProductCategoryVO> categoryMap) {
        ProductVO vo = new ProductVO();
        vo.setId(product.getId());
        vo.setCategoryId(product.getCategoryId());
        if (product.getCategoryId() != null) {
            ProductCategoryVO category = categoryMap.get(product.getCategoryId());
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

    private ProductDetailVO convertToDetailVO(Product product, Map<Long, ProductCategoryVO> categoryMap) {
        ProductDetailVO vo = new ProductDetailVO();
        vo.setId(product.getId());
        vo.setCategoryId(product.getCategoryId());
        if (product.getCategoryId() != null) {
            ProductCategoryVO category = categoryMap.get(product.getCategoryId());
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
