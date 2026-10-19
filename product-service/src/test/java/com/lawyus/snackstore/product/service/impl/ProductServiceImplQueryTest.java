package com.lawyus.snackstore.product.service.impl;

import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.conditions.Wrapper;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.lawyus.snackstore.common.dto.ProductSearchDTO;
import com.lawyus.snackstore.common.response.PageResult;
import com.lawyus.snackstore.product.exception.BusinessException;
import com.lawyus.snackstore.product.model.dto.ProductDTO;
import com.lawyus.snackstore.product.model.dto.ProductQueryDTO;
import com.lawyus.snackstore.product.model.entity.Product;
import com.lawyus.snackstore.product.model.event.ProductChangedEvent;
import com.lawyus.snackstore.product.model.event.ProductChangedEvent.ChangeType;
import com.lawyus.snackstore.product.model.vo.ProductVO;
import com.lawyus.snackstore.product.repository.ProductMapper;
import com.lawyus.snackstore.product.service.ProductCategoryService;
import com.lawyus.snackstore.product.service.ProductSnapshotService;
import org.apache.ibatis.builder.MapperBuilderAssistant;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InOrder;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;

import java.math.BigDecimal;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * 商品查询与写操作的特征化测试，重点锁三件容易被改坏的事：
 * 排序字段白名单（防注入）、搜索降级 MySQL 时默认只出上架商品、以及每次写操作都必须发变更事件
 * （否则 ES 索引与商品表静默漂移）。
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("ProductServiceImpl 查询口径与事件发布")
class ProductServiceImplQueryTest {

    @Mock
    private ProductMapper productMapper;

    @Mock
    private ProductCategoryService categoryService;

    @Mock
    private ProductSnapshotService snapshotService;

    @Mock
    private ApplicationEventPublisher eventPublisher;

    @InjectMocks
    private ProductServiceImpl productService;

    @BeforeAll
    static void installLambdaColumnCache() {
        MapperBuilderAssistant assistant =
                new MapperBuilderAssistant(new MybatisConfiguration(), "");
        assistant.setCurrentNamespace(ProductMapper.class.getName());
        TableInfoHelper.initTableInfo(assistant, Product.class);
    }

    private static Product product(long id, String name, BigDecimal price, int stock, int status) {
        Product p = new Product();
        p.setId(id);
        p.setName(name);
        p.setPrice(price);
        p.setStock(stock);
        p.setStatus(status);
        return p;
    }

    @SuppressWarnings("unchecked")
    private LambdaQueryWrapper<Product> captureQueryWrapper() {
        ArgumentCaptor<Wrapper<Product>> captor = ArgumentCaptor.forClass(Wrapper.class);
        verify(productMapper).selectPage(any(), captor.capture());
        return (LambdaQueryWrapper<Product>) captor.getValue();
    }

    @Test
    @DisplayName("排序字段白名单外的值直接拒绝，且不下达任何查询")
    void rejectsSortFieldOutsideWhitelist() {
        ProductQueryDTO query = new ProductQueryDTO();
        query.setSortField("password");

        assertThatThrownBy(() -> productService.getProductList(query))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("不支持的排序字段: password");

        verifyNoInteractions(productMapper);
    }

    @Test
    @DisplayName("未指定排序字段时默认按创建时间倒序")
    void defaultsToCreatedAtDesc() {
        when(productMapper.selectPage(any(), any())).thenReturn(new Page<>(1, 10));

        productService.getProductList(new ProductQueryDTO());

        String sql = captureQueryWrapper().getSqlSegment().toUpperCase();
        assertThat(sql).contains("ORDER BY");
        assertThat(sql).contains("DESC");
    }

    @Test
    @DisplayName("白名单内字段可升序排序（price:asc）")
    void allowsWhitelistedSortFieldWithAsc() {
        when(productMapper.selectPage(any(), any())).thenReturn(new Page<>(1, 10));

        ProductQueryDTO query = new ProductQueryDTO();
        query.setSortField("price");
        query.setSortOrder("asc");
        productService.getProductList(query);

        String sql = captureQueryWrapper().getSqlSegment().toUpperCase();
        assertThat(sql).contains("PRICE").contains("ASC");
    }

    @Test
    @DisplayName("搜索降级到 MySQL 时，status 为空必须补 1（只出上架商品）")
    void searchFallbackDefaultsToOnSaleStatus() {
        when(productMapper.selectPage(any(), any())).thenReturn(new Page<>(1, 10));

        productService.searchByKeyword(new ProductSearchDTO());

        LambdaQueryWrapper<Product> wrapper = captureQueryWrapper();
        // MyBatis-Plus 的 paramNameValuePairs 是惰性的：只在生成 SQL 片段时才写入条件值。
        // 必须先调 getSqlSegment() 再断言参数值，否则拿到空 Map（单跑与成组跑结果会不一致）。
        assertThat(wrapper.getSqlSegment()).contains("status =");
        assertThat(wrapper.getParamNameValuePairs().values()).containsExactly(1);
    }

    @Test
    @DisplayName("显式传 status=0 时不得被默认值覆盖")
    void searchKeepsExplicitStatusFilter() {
        when(productMapper.selectPage(any(), any())).thenReturn(new Page<>(1, 10));
        ProductSearchDTO search = new ProductSearchDTO();
        search.setStatus(0);

        productService.searchByKeyword(search);

        LambdaQueryWrapper<Product> explicit = captureQueryWrapper();
        assertThat(explicit.getSqlSegment()).contains("status =");
        assertThat(explicit.getParamNameValuePairs().values()).containsExactly(0);
    }

    @Test
    @DisplayName("批量按 ID 查询保持入参顺序，并丢弃不存在的 ID")
    void getByIdsPreservesRequestOrderAndDropsMissing() {
        when(productMapper.selectByIds(anyCollection())).thenReturn(List.of(
                product(1L, "A", new BigDecimal("1.00"), 5, 1),
                product(3L, "C", new BigDecimal("3.00"), 5, 1)));

        List<ProductVO> result = productService.getProductListByIds(List.of(3L, 1L, 2L));

        assertThat(result).extracting(ProductVO::getId).containsExactly(3L, 1L);
    }

    @Test
    @DisplayName("商品不存在时 getProductEntity 抛 PRODUCT_NOT_FOUND")
    void missingProductThrowsBusinessException() {
        when(productMapper.selectById(9L)).thenReturn(null);

        assertThatThrownBy(() -> productService.getProductEntity(9L))
                .isInstanceOf(BusinessException.class);
    }

    @Test
    @DisplayName("新建商品：库存与状态缺省补 0/1，写快照并发 CREATED 事件")
    void createProductAppliesDefaultsAndPublishesCreatedEvent() {
        when(productMapper.insert(any(Product.class))).thenAnswer(invocation -> {
            invocation.getArgument(0, Product.class).setId(55L);
            return 1;
        });
        ProductDTO dto = new ProductDTO();
        dto.setName("新零食");
        dto.setPrice(new BigDecimal("9.90"));

        productService.createProduct(dto);

        ArgumentCaptor<Product> captor = ArgumentCaptor.forClass(Product.class);
        verify(productMapper).insert(captor.capture());
        assertThat(captor.getValue().getStock()).isZero();
        assertThat(captor.getValue().getStatus()).isEqualTo(1);

        verify(snapshotService).createSnapshot(any(Product.class));
        verify(eventPublisher).publishEvent(new ProductChangedEvent(55L, ChangeType.CREATED));
    }

    @Test
    @DisplayName("更新商品：只覆盖非 null 字段，且必须先存快照再落库")
    void updateProductAppliesPartialChangesAndSnapshotsFirst() {
        Product existing = product(1L, "旧名", new BigDecimal("1.00"), 10, 1);
        when(productMapper.selectById(1L)).thenReturn(existing);

        ProductDTO patch = new ProductDTO();
        patch.setName("新名");
        productService.updateProduct(1L, patch);

        InOrder order = inOrder(productMapper, snapshotService);
        order.verify(snapshotService).createSnapshot(any(Product.class));
        order.verify(productMapper).updateById(any(Product.class));

        ArgumentCaptor<Product> captor = ArgumentCaptor.forClass(Product.class);
        verify(productMapper).updateById(captor.capture());
        assertThat(captor.getValue().getName()).isEqualTo("新名");
        assertThat(captor.getValue().getPrice()).isEqualByComparingTo("1.00");
        assertThat(captor.getValue().getStock()).isEqualTo(10);
        verify(eventPublisher).publishEvent(new ProductChangedEvent(1L, ChangeType.UPDATED));
    }

    @Test
    @DisplayName("删除商品：存快照后删除并发 DELETED 事件（下架靠状态，删除靠事件清索引）")
    void deleteProductSnapshotsAndPublishesDeletedEvent() {
        when(productMapper.selectById(1L)).thenReturn(product(1L, "A", new BigDecimal("1.00"), 10, 1));

        productService.deleteProduct(1L);

        verify(snapshotService).createSnapshot(any(Product.class));
        verify(productMapper).deleteById(1L);
        verify(eventPublisher).publishEvent(new ProductChangedEvent(1L, ChangeType.DELETED));
    }

    @Test
    @DisplayName("上下架走状态字段并广播 STATUS_CHANGED，删除未受影响")
    void statusChangePublishesStatusChangedEvent() {
        when(productMapper.selectById(1L)).thenReturn(product(1L, "A", new BigDecimal("1.00"), 10, 1));

        productService.updateProductStatus(1L, 0);

        ArgumentCaptor<Product> captor = ArgumentCaptor.forClass(Product.class);
        verify(productMapper).updateById(captor.capture());
        assertThat(captor.getValue().getStatus()).isZero();
        verify(productMapper, never()).deleteById(1L);
        verify(eventPublisher).publishEvent(new ProductChangedEvent(1L, ChangeType.STATUS_CHANGED));
    }
}
