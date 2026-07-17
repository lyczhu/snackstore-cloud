package com.lawyus.snackstore.product.search.listener;

import com.lawyus.snackstore.common.message.ProductSearchSyncMessage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.support.KafkaHeaders;
import org.springframework.messaging.handler.annotation.Header;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.stereotype.Component;

@Component
public class ProductSearchSyncDltConsumer {

    private static final Logger log = LoggerFactory.getLogger(ProductSearchSyncDltConsumer.class);

    @KafkaListener(topics = "${spring.kafka.topic.product-changed:product-changed}.DLT",
            groupId = "${spring.kafka.consumer.group-id:product-search-group}-dlt")
    public void handleDltMessage(@Payload(required = false) ProductSearchSyncMessage message,
                                 @Header(KafkaHeaders.RECEIVED_PARTITION) int partition,
                                 @Header(KafkaHeaders.OFFSET) long offset,
                                 @Header(KafkaHeaders.EXCEPTION_MESSAGE) String exceptionMessage) {
        if (message == null) {
            log.error("死信队列收到空消息: partition={}, offset={}, exception={}", partition, offset, exceptionMessage);
            return;
        }
        log.error("商品变更消息进入死信队列: productId={}, type={}, eventId={}, partition={}, offset={}, exception={}",
                message.getId(), message.getChangeType(), message.getEventId(), partition, offset, exceptionMessage);
    }
}
