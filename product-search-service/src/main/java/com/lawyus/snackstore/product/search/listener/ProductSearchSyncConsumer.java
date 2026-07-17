package com.lawyus.snackstore.product.search.listener;

import com.lawyus.snackstore.common.message.ProductSearchSyncMessage;
import com.lawyus.snackstore.product.search.service.ProductSearchIndexService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.support.Acknowledgment;
import org.springframework.kafka.support.KafkaHeaders;
import org.springframework.messaging.handler.annotation.Header;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.stereotype.Component;

@Component
public class ProductSearchSyncConsumer {

    private static final Logger log = LoggerFactory.getLogger(ProductSearchSyncConsumer.class);

    private final ProductSearchIndexService searchIndexService;

    public ProductSearchSyncConsumer(ProductSearchIndexService searchIndexService) {
        this.searchIndexService = searchIndexService;
    }

    @KafkaListener(topics = "${spring.kafka.topic.product-changed:product-changed}",
            groupId = "${spring.kafka.consumer.group-id:product-search-group}")
    public void handleProductChanged(@Payload ProductSearchSyncMessage message,
                                     Acknowledgment ack,
                                     @Header(KafkaHeaders.RECEIVED_PARTITION) int partition,
                                     @Header(KafkaHeaders.OFFSET) long offset) {
        if (message == null || message.getId() == null) {
            log.warn("收到空消息或消息ID为空，partition={}, offset={}", partition, offset);
            ack.acknowledge();
            return;
        }

        String eventId = message.getEventId();
        Long productId = message.getId();
        ProductSearchSyncMessage.ChangeType changeType = message.getChangeType();
        log.info("收到商品变更消息: productId={}, type={}, eventId={}, partition={}, offset={}",
                productId, changeType, eventId, partition, offset);

        long startTime = System.currentTimeMillis();
        try {
            if (changeType == ProductSearchSyncMessage.ChangeType.DELETED) {
                searchIndexService.delete(productId);
                log.info("商品索引已删除: productId={}, eventId={}, 耗时{}ms",
                        productId, eventId, System.currentTimeMillis() - startTime);
            } else {
                searchIndexService.save(message);
                log.info("商品索引已更新: productId={}, type={}, eventId={}, 耗时{}ms",
                        productId, changeType, eventId, System.currentTimeMillis() - startTime);
            }
            ack.acknowledge();
        } catch (Exception e) {
            log.error("处理商品变更消息失败: productId={}, type={}, eventId={}, partition={}, offset={}",
                    productId, changeType, eventId, partition, offset, e);
            throw e;
        }
    }
}
