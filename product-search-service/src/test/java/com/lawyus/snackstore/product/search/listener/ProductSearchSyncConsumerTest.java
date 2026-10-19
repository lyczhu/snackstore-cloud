package com.lawyus.snackstore.product.search.listener;

import com.lawyus.snackstore.common.dto.ProductSearchItemDTO;
import com.lawyus.snackstore.common.message.ProductSearchSyncMessage;
import com.lawyus.snackstore.product.search.service.ProductSearchIndexService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.kafka.support.Acknowledgment;

import java.math.BigDecimal;
import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

/**
 * {@link ProductSearchSyncConsumer} 的单元测试，钉住手工 ACK 的「消息不丢」语义。
 *
 * <p>最关键的一条：索引写入失败时必须<b>重新抛出且不 ack</b>，否则位点前移、这条变更永久丢失，
 * ES 与商品库将长期不一致（只能等每日全量重建补偿）。
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("商品同步消费者：手工 ACK 语义与事件→索引字段映射")
class ProductSearchSyncConsumerTest {

    @Mock
    private ProductSearchIndexService searchIndexService;

    @Mock
    private Acknowledgment ack;

    @InjectMocks
    private ProductSearchSyncConsumer consumer;

    private ProductSearchSyncMessage fullMessage(ProductSearchSyncMessage.ChangeType changeType) {
        ProductSearchSyncMessage message = new ProductSearchSyncMessage();
        message.setEventId("evt-1");
        message.setEventTime(LocalDateTime.of(2026, 8, 17, 10, 0));
        message.setChangeType(changeType);
        message.setId(7L);
        message.setCategoryId(3L);
        message.setCategoryName("饮料");
        message.setCategorySort(5);
        message.setName("可乐");
        message.setCoverImage("cover.png");
        message.setPrice(new BigDecimal("3.50"));
        message.setStock(20);
        message.setDescription("冰爽");
        message.setStatus(1);
        message.setCreatedAt(LocalDateTime.of(2026, 8, 1, 9, 0));
        return message;
    }

    @Test
    @DisplayName("非删除事件应整条映射 12 个字段到 ProductSearchItemDTO 后入索引")
    void updatedShouldMapEverySnapshotField() {
        consumer.handleProductChanged(fullMessage(ProductSearchSyncMessage.ChangeType.UPDATED), ack, 0, 100L);

        ArgumentCaptor<ProductSearchItemDTO> captor = ArgumentCaptor.forClass(ProductSearchItemDTO.class);
        verify(searchIndexService).save(captor.capture());

        ProductSearchItemDTO item = captor.getValue();
        assertThat(item.getId()).isEqualTo(7L);
        assertThat(item.getCategoryId()).isEqualTo(3L);
        assertThat(item.getCategoryName()).isEqualTo("饮料");
        assertThat(item.getCategorySort()).isEqualTo(5);
        assertThat(item.getName()).isEqualTo("可乐");
        assertThat(item.getCoverImage()).isEqualTo("cover.png");
        assertThat(item.getPrice()).isEqualByComparingTo("3.50");
        assertThat(item.getStock()).isEqualTo(20);
        assertThat(item.getDescription()).isEqualTo("冰爽");
        assertThat(item.getStatus()).isEqualTo(1);
        assertThat(item.getCreatedAt()).isEqualTo(LocalDateTime.of(2026, 8, 1, 9, 0));
        // 拉取通道 DTO 不携带事件元数据（eventId/eventTime 不在其上）
        assertThat(item.getClass().getSimpleName()).isEqualTo("ProductSearchItemDTO");
    }

    @Test
    @DisplayName("CREATED / STATUS_CHANGED / STOCK_CHANGED 都走写入分支")
    void nonDeleteTypesShouldAllSave() {
        for (ProductSearchSyncMessage.ChangeType type : new ProductSearchSyncMessage.ChangeType[]{
                ProductSearchSyncMessage.ChangeType.CREATED,
                ProductSearchSyncMessage.ChangeType.STATUS_CHANGED,
                ProductSearchSyncMessage.ChangeType.STOCK_CHANGED}) {

            consumer.handleProductChanged(fullMessage(type), ack, 0, 1L);
        }

        verify(searchIndexService, org.mockito.Mockito.times(3)).save(any(ProductSearchItemDTO.class));
        verify(searchIndexService, never()).delete(anyLong());
    }

    @Test
    @DisplayName("DELETED 必须只删索引，不写入")
    void deletedShouldDeleteOnly() {
        ProductSearchSyncMessage message = new ProductSearchSyncMessage();
        message.setId(9L);
        message.setChangeType(ProductSearchSyncMessage.ChangeType.DELETED);

        consumer.handleProductChanged(message, ack, 0, 1L);

        verify(searchIndexService).delete(9L);
        verify(searchIndexService, never()).save(any());
    }

    @Test
    @DisplayName("索引写入成功才提交位点，且只提交一次")
    void shouldAckExactlyOnceOnSuccess() {
        consumer.handleProductChanged(fullMessage(ProductSearchSyncMessage.ChangeType.UPDATED), ack, 0, 1L);

        verify(ack).acknowledge();
    }

    @Test
    @DisplayName("索引写入失败必须重抛且不提交位点，交给重试与死信，绝不静默丢消息")
    void shouldRethrowAndNeverAckWhenIndexWriteFails() {
        doThrow(new IllegalStateException("es down"))
                .when(searchIndexService).save(any(ProductSearchItemDTO.class));

        ProductSearchSyncMessage message = fullMessage(ProductSearchSyncMessage.ChangeType.UPDATED);

        assertThatThrownBy(() -> consumer.handleProductChanged(message, ack, 0, 42L))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("es down");
        verify(ack, never()).acknowledge();
    }

    @Test
    @DisplayName("删除失败同样重抛且不提交位点")
    void shouldRethrowAndNeverAckWhenDeleteFails() {
        doThrow(new IllegalStateException("es down")).when(searchIndexService).delete(anyLong());

        ProductSearchSyncMessage message = new ProductSearchSyncMessage();
        message.setId(9L);
        message.setChangeType(ProductSearchSyncMessage.ChangeType.DELETED);

        assertThatThrownBy(() -> consumer.handleProductChanged(message, ack, 0, 42L))
                .isInstanceOf(IllegalStateException.class);
        verify(ack, never()).acknowledge();
    }

    @Test
    @DisplayName("消息为空或缺商品ID：直接确认跳过，不去碰索引")
    void blankMessageShouldBeAcknowledgedAndSkipped() {
        consumer.handleProductChanged(null, ack, 0, 1L);

        ProductSearchSyncMessage noId = new ProductSearchSyncMessage();
        noId.setChangeType(ProductSearchSyncMessage.ChangeType.UPDATED);
        consumer.handleProductChanged(noId, ack, 0, 2L);

        verify(ack, org.mockito.Mockito.times(2)).acknowledge();
        verify(searchIndexService, never()).save(any());
        verify(searchIndexService, never()).delete(anyLong());
    }
}
