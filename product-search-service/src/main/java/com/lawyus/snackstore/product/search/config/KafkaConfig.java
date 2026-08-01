package com.lawyus.snackstore.product.search.config;

import lombok.extern.slf4j.Slf4j;
import org.apache.kafka.clients.consumer.Consumer;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.common.TopicPartition;
import org.jspecify.annotations.NonNull;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.annotation.EnableKafka;
import org.springframework.kafka.core.KafkaOperations;
import org.springframework.kafka.listener.CommonErrorHandler;
import org.springframework.kafka.listener.DeadLetterPublishingRecoverer;
import org.springframework.kafka.listener.DefaultErrorHandler;
import org.springframework.kafka.support.serializer.DeserializationException;
import org.springframework.messaging.converter.MessageConversionException;
import org.springframework.util.backoff.FixedBackOff;

@EnableKafka
@Configuration
@Slf4j
public class KafkaConfig {

    @Bean
    public CommonErrorHandler kafkaErrorHandler(KafkaOperations<String, Object> kafkaOperations) {
        DeadLetterPublishingRecoverer recoverer = new DeadLetterPublishingRecoverer(kafkaOperations,
                (consumerRecord, exception) ->
                        new TopicPartition(consumerRecord.topic() + ".DLT", consumerRecord.partition())) {
            @Override
            public void accept(@NonNull ConsumerRecord<?, ?> record, Consumer<?, ?> consumer, @NonNull Exception exception) {
                if (isFatalConversionFailure(exception)) {
                    log.warn("消息无法反序列化/转换，直接丢弃且不进死信队列: topic={}, partition={}, offset={}, exception={}",
                            record.topic(), record.partition(), record.offset(), exception.getMessage());
                    return;
                }
                super.accept(record, consumer, exception);
            }
        };
        DefaultErrorHandler errorHandler = new DefaultErrorHandler(recoverer, new FixedBackOff(1000L, 3L));
        errorHandler.addNotRetryableExceptions(IllegalArgumentException.class, MessageConversionException.class);
        return errorHandler;
    }

    private static boolean isFatalConversionFailure(Exception exception) {
        Throwable cause = exception;
        while (cause != null) {
            if (cause instanceof MessageConversionException || cause instanceof DeserializationException) {
                return true;
            }
            cause = cause.getCause();
        }
        return false;
    }
}
