package com.lawyus.snackstore.product.listener;

import com.lawyus.snackstore.common.message.ProductSearchSyncMessage;
import com.lawyus.snackstore.product.model.entity.Product;
import com.lawyus.snackstore.product.model.entity.ProductCategory;
import com.lawyus.snackstore.product.model.event.ProductChangedEvent;
import com.lawyus.snackstore.product.repository.ProductCategoryMapper;
import com.lawyus.snackstore.product.repository.ProductMapper;
import org.apache.kafka.clients.producer.RecordMetadata;
import org.springframework.kafka.support.SendResult;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.test.util.ReflectionTestUtils;

import java.math.BigDecimal;
import java.util.concurrent.CompletableFuture;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * {@link ProductChangedListener} 的单元测试：商品变更事件 → Kafka 同步消息的发布端契约。
 *
 * <p>要钉住的语义：
 * <ul>
 *   <li>只发送商品<b>当前态</b>（读库组装），删除事件只带 id + DELETED；</li>
 *   <li>库里已查不到商品时降级为删除消息，避免索引里残留幽灵商品；</li>
 *   <li>消息 key 为 {@code product-{id}}，保证同一商品落到同一分区、顺序消费；</li>
 *   <li>发送失败按 backoff 重试，次数耗尽后放弃（等全量重建补偿），且<b>绝不把异常抛回业务线程</b>——
 *       此刻事务已提交，抛出既回滚不了也会污染调用方。</li>
 * </ul>
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
@DisplayName("商品变更事件发布：消息组装、分区键与失败重试")
class ProductChangedListenerTest {

    @Mock
    private ProductMapper productMapper;

    @Mock
    private ProductCategoryMapper categoryMapper;

    @Mock
    private KafkaTemplate<String, Object> kafkaTemplate;

    private ProductChangedListener listener;

    /**
     * 成功回包必须在任何 {@code when(...)} 开始之前构好：SendResult 的桩本身也要打桩，
     * 若写成 {@code thenReturn(succeeded())} 会在外层打桩未完成时嵌套打桩，抛 UnfinishedStubbingException。
     */
    private CompletableFuture<SendResult<String, Object>> okFuture;

    @BeforeEach
    void setUp() {
        listener = new ProductChangedListener(productMapper, categoryMapper, kafkaTemplate);
        ReflectionTestUtils.setField(listener, "productChangedTopic", "product-changed");
        ReflectionTestUtils.setField(listener, "maxAttempts", 1);
        ReflectionTestUtils.setField(listener, "backoffMs", 0L);
        okFuture = succeeded();
    }

    @SuppressWarnings("unchecked")
    private CompletableFuture<SendResult<String, Object>> succeeded() {
        SendResult<String, Object> result = mock(SendResult.class);
        RecordMetadata metadata = mock(RecordMetadata.class);
        org.mockito.Mockito.doReturn(metadata).when(result).getRecordMetadata();
        return CompletableFuture.completedFuture(result);
    }

    @SuppressWarnings("unchecked")
    private CompletableFuture<SendResult<String, Object>> failed(String reason) {
        CompletableFuture<SendResult<String, Object>> future = new CompletableFuture<>();
        future.completeExceptionally(new RuntimeException(reason));
        return future;
    }

    private Product product() {
        Product product = new Product();
        product.setId(7L);
        product.setCategoryId(3L);
        product.setName("可乐");
        product.setCoverImage("cover.png");
        product.setPrice(new BigDecimal("3.50"));
        product.setStock(20);
        product.setDescription("冰爽");
        product.setStatus(1);
        return product;
    }

    @Test
    @DisplayName("更新事件应按商品当前态与分类信息组装消息")
    void updatedEventShouldPublishFullSnapshotMessage() {
        ProductCategory category = new ProductCategory();
        category.setName("饮料");
        category.setSort(5);
        when(productMapper.selectById(7L)).thenReturn(product());
        when(categoryMapper.selectById(3L)).thenReturn(category);
        when(kafkaTemplate.send(anyString(), anyString(), any())).thenReturn(okFuture);

        listener.handleProductChanged(new ProductChangedEvent(7L, ProductChangedEvent.ChangeType.UPDATED));

        ArgumentCaptor<Object> captor = ArgumentCaptor.forClass(Object.class);
        verify(kafkaTemplate).send(eq("product-changed"), eq("product-7"), captor.capture());

        ProductSearchSyncMessage message = (ProductSearchSyncMessage) captor.getValue();
        assertThat(message.getId()).isEqualTo(7L);
        assertThat(message.getCategoryId()).isEqualTo(3L);
        assertThat(message.getCategoryName()).isEqualTo("饮料");
        assertThat(message.getCategorySort()).isEqualTo(5);
        assertThat(message.getName()).isEqualTo("可乐");
        assertThat(message.getPrice()).isEqualByComparingTo("3.50");
        assertThat(message.getStock()).isEqualTo(20);
        assertThat(message.getStatus()).isEqualTo(1);
        assertThat(message.getChangeType()).isEqualTo(ProductSearchSyncMessage.ChangeType.UPDATED);
    }

    @Test
    @DisplayName("删除事件只带商品ID与 DELETED，不去查库")
    void deletedEventShouldPublishTombstoneOnly() {
        when(kafkaTemplate.send(anyString(), anyString(), any())).thenReturn(okFuture);

        listener.handleProductChanged(new ProductChangedEvent(7L, ProductChangedEvent.ChangeType.DELETED));

        ArgumentCaptor<Object> captor = ArgumentCaptor.forClass(Object.class);
        verify(kafkaTemplate).send(eq("product-changed"), eq("product-7"), captor.capture());

        ProductSearchSyncMessage message = (ProductSearchSyncMessage) captor.getValue();
        assertThat(message.getId()).isEqualTo(7L);
        assertThat(message.getChangeType()).isEqualTo(ProductSearchSyncMessage.ChangeType.DELETED);
        assertThat(message.getName()).isNull();
        verify(productMapper, never()).selectById(any());
    }

    @Test
    @DisplayName("库里已查不到商品时降级为删除消息，避免索引残留幽灵商品")
    void missingProductShouldDowngradeToDeleteMessage() {
        when(productMapper.selectById(7L)).thenReturn(null);
        when(kafkaTemplate.send(anyString(), anyString(), any())).thenReturn(okFuture);

        listener.handleProductChanged(new ProductChangedEvent(7L, ProductChangedEvent.ChangeType.UPDATED));

        ArgumentCaptor<Object> captor = ArgumentCaptor.forClass(Object.class);
        verify(kafkaTemplate).send(eq("product-changed"), eq("product-7"), captor.capture());
        assertThat(((ProductSearchSyncMessage) captor.getValue()).getChangeType())
                .isEqualTo(ProductSearchSyncMessage.ChangeType.DELETED);
    }

    @Test
    @DisplayName("商品未挂分类时不去查分类表，分类字段留空")
    void nullCategoryIdShouldSkipCategoryLookup() {
        Product product = product();
        product.setCategoryId(null);
        when(productMapper.selectById(7L)).thenReturn(product);
        when(kafkaTemplate.send(anyString(), anyString(), any())).thenReturn(okFuture);

        listener.handleProductChanged(new ProductChangedEvent(7L, ProductChangedEvent.ChangeType.CREATED));

        verify(categoryMapper, never()).selectById(any());
        ArgumentCaptor<Object> captor = ArgumentCaptor.forClass(Object.class);
        verify(kafkaTemplate).send(anyString(), anyString(), captor.capture());
        ProductSearchSyncMessage message = (ProductSearchSyncMessage) captor.getValue();
        assertThat(message.getCategoryId()).isNull();
        assertThat(message.getCategoryName()).isNull();
        assertThat(message.getChangeType()).isEqualTo(ProductSearchSyncMessage.ChangeType.CREATED);
    }

    @Test
    @DisplayName("五种变更类型都要原样映射到消息枚举，不能退化成一个")
    void everyChangeTypeShouldBeMapped() {
        when(productMapper.selectById(7L)).thenReturn(product());
        when(kafkaTemplate.send(anyString(), anyString(), any())).thenReturn(okFuture);

        for (ProductChangedEvent.ChangeType type : ProductChangedEvent.ChangeType.values()) {
            if (type == ProductChangedEvent.ChangeType.DELETED) {
                continue;
            }
            listener.handleProductChanged(new ProductChangedEvent(7L, type));
        }

        ArgumentCaptor<Object> captor = ArgumentCaptor.forClass(Object.class);
        verify(kafkaTemplate, times(4)).send(anyString(), anyString(), captor.capture());
        assertThat(captor.getAllValues())
                .extracting(v -> ((ProductSearchSyncMessage) v).getChangeType())
                .containsExactly(
                        ProductSearchSyncMessage.ChangeType.CREATED,
                        ProductSearchSyncMessage.ChangeType.UPDATED,
                        ProductSearchSyncMessage.ChangeType.STATUS_CHANGED,
                        ProductSearchSyncMessage.ChangeType.STOCK_CHANGED);
    }

    @Test
    @DisplayName("发送失败且重试次数已耗尽时放弃并留痕，不抛回业务线程")
    void sendFailureShouldBeSwallowedWhenRetriesExhausted() throws InterruptedException {
        when(productMapper.selectById(7L)).thenReturn(product());
        java.util.concurrent.atomic.AtomicInteger calls = new java.util.concurrent.atomic.AtomicInteger();
        when(kafkaTemplate.send(anyString(), anyString(), any())).thenAnswer(invocation -> {
            calls.incrementAndGet();
            return failed("broker down");
        });

        assertThatCode(() -> listener.handleProductChanged(
                new ProductChangedEvent(7L, ProductChangedEvent.ChangeType.UPDATED)))
                .doesNotThrowAnyException();
        // maxAttempts=1：首次失败即耗尽预算，不再重投
        Thread.sleep(100L);
        assertThat(calls.get()).isEqualTo(1);
    }

    @Test
    @DisplayName("允许一次重试时应再发一次，最终成功")
    void sendFailureShouldRetryOnceWhenBudgetAllows() throws InterruptedException {
        ReflectionTestUtils.setField(listener, "maxAttempts", 2);
        when(productMapper.selectById(7L)).thenReturn(product());
        java.util.concurrent.atomic.AtomicInteger calls = new java.util.concurrent.atomic.AtomicInteger();
        when(kafkaTemplate.send(anyString(), anyString(), any())).thenAnswer(invocation ->
                calls.incrementAndGet() == 1 ? failed("broker down") : okFuture);

        listener.handleProductChanged(new ProductChangedEvent(7L, ProductChangedEvent.ChangeType.UPDATED));

        // backoffMs=0，重投由守护线程池调度，轮询等待最多 2 秒
        long deadline = System.currentTimeMillis() + 2_000L;
        while (System.currentTimeMillis() < deadline && calls.get() < 2) {
            Thread.sleep(10L);
        }
        verify(kafkaTemplate, times(2)).send(eq("product-changed"), eq("product-7"), any());
    }
}
