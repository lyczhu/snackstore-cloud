package com.lawyus.snackstore.product.listener;

import com.lawyus.snackstore.common.message.ProductSearchSyncMessage;
import com.lawyus.snackstore.product.model.entity.Product;
import com.lawyus.snackstore.product.model.entity.ProductCategory;
import com.lawyus.snackstore.product.model.event.ProductChangedEvent;
import com.lawyus.snackstore.product.repository.ProductCategoryMapper;
import com.lawyus.snackstore.product.repository.ProductMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

@Component
public class ProductChangedListener {

    private static final Logger log = LoggerFactory.getLogger(ProductChangedListener.class);

    private final ProductMapper productMapper;
    private final ProductCategoryMapper categoryMapper;
    private final KafkaTemplate<String, Object> kafkaTemplate;

    @Value("${spring.kafka.topic.product-changed:product-changed}")
    private String productChangedTopic;

    public ProductChangedListener(ProductMapper productMapper,
                                  ProductCategoryMapper categoryMapper,
                                  KafkaTemplate<String, Object> kafkaTemplate) {
        this.productMapper = productMapper;
        this.categoryMapper = categoryMapper;
        this.kafkaTemplate = kafkaTemplate;
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT, fallbackExecution = true)
    public void handleProductChanged(ProductChangedEvent event) {
        Long productId = event.productId();
        ProductChangedEvent.ChangeType changeType = event.type();
        log.info("事务提交后处理商品变更事件: productId={}, type={}", productId, changeType);

        try {
            ProductSearchSyncMessage message;
            if (changeType == ProductChangedEvent.ChangeType.DELETED) {
                message = buildDeleteMessage(productId);
            } else {
                Product product = productMapper.selectById(productId);
                if (product == null) {
                    log.warn("商品不存在，可能已被删除: productId={}", productId);
                    message = buildDeleteMessage(productId);
                } else {
                    ProductCategory category = null;
                    if (product.getCategoryId() != null) {
                        category = categoryMapper.selectById(product.getCategoryId());
                    }
                    message = buildSyncMessage(product, category, changeType);
                }
            }

            String key = "product-" + productId;
            kafkaTemplate.send(productChangedTopic, key, message)
                    .whenComplete((result, ex) -> {
                        if (ex != null) {
                            log.error("Kafka消息发送失败: productId={}, eventId={}", productId, message.getEventId(), ex);
                        } else {
                            log.debug("Kafka消息发送成功: productId={}, eventId={}, partition={}, offset={}",
                                    productId, message.getEventId(),
                                    result.getRecordMetadata().partition(),
                                    result.getRecordMetadata().offset());
                        }
                    });
        } catch (Exception e) {
            log.error("处理商品变更事件异常: productId={}, type={}", productId, changeType, e);
        }
    }

    private ProductSearchSyncMessage buildDeleteMessage(Long productId) {
        ProductSearchSyncMessage message = new ProductSearchSyncMessage();
        message.setId(productId);
        message.setChangeType(ProductSearchSyncMessage.ChangeType.DELETED);
        return message;
    }

    private ProductSearchSyncMessage buildSyncMessage(Product product, ProductCategory category,
                                                      ProductChangedEvent.ChangeType changeType) {
        ProductSearchSyncMessage message = new ProductSearchSyncMessage();
        message.setId(product.getId());
        message.setCategoryId(product.getCategoryId());
        if (category != null) {
            message.setCategoryName(category.getName());
            message.setCategorySort(category.getSort());
        }
        message.setName(product.getName());
        message.setCoverImage(product.getCoverImage());
        message.setPrice(product.getPrice());
        message.setStock(product.getStock());
        message.setDescription(product.getDescription());
        message.setStatus(product.getStatus());
        message.setCreatedAt(product.getCreatedAt());
        message.setChangeType(convertChangeType(changeType));
        return message;
    }

    private ProductSearchSyncMessage.ChangeType convertChangeType(ProductChangedEvent.ChangeType type) {
        return switch (type) {
            case CREATED -> ProductSearchSyncMessage.ChangeType.CREATED;
            case UPDATED -> ProductSearchSyncMessage.ChangeType.UPDATED;
            case DELETED -> ProductSearchSyncMessage.ChangeType.DELETED;
            case STATUS_CHANGED -> ProductSearchSyncMessage.ChangeType.STATUS_CHANGED;
            case STOCK_CHANGED -> ProductSearchSyncMessage.ChangeType.STOCK_CHANGED;
        };
    }
}
