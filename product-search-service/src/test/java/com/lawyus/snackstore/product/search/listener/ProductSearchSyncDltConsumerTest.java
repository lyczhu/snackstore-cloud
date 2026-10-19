package com.lawyus.snackstore.product.search.listener;

import com.lawyus.snackstore.common.message.ProductSearchSyncMessage;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.kafka.support.Acknowledgment;

import static org.mockito.Mockito.verify;

/**
 * {@link ProductSearchSyncDltConsumer} 的单元测试。
 *
 * <p>死信消费者是同步链路的最后一道兜底：它唯一的职责是把消息从死信 topic 上消费掉并留下可查证据，
 * 因此<b>任何分支都必须 ack</b>，否则死信分区会无限堆积、消费者反复重投。
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("商品同步死信消费者：任何分支都要确认，避免死信堆积")
class ProductSearchSyncDltConsumerTest {

    @Mock
    private Acknowledgment ack;

    private final ProductSearchSyncDltConsumer consumer = new ProductSearchSyncDltConsumer();

    @Test
    @DisplayName("携带完整上下文记录死信消息后确认位点")
    void shouldLogAndAckDltMessage() {
        ProductSearchSyncMessage message = new ProductSearchSyncMessage();
        message.setEventId("evt-1");
        message.setId(7L);
        message.setChangeType(ProductSearchSyncMessage.ChangeType.UPDATED);

        consumer.handleDltMessage(message, ack, 0, 5L, "es down");

        verify(ack).acknowledge();
    }

    @Test
    @DisplayName("反序列化失败导致消息为空时也要确认，不能卡住死信分区")
    void shouldAckEvenWhenPayloadMissing() {
        consumer.handleDltMessage(null, ack, 0, 5L, "deserialization error");

        verify(ack).acknowledge();
    }
}
