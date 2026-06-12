package com.lawyus.snackstore.user.domain.common.event;

import java.util.List;

public interface DomainEventPublisher {

    void publish(BaseDomainEvent event);

    void publishAll(List<BaseDomainEvent> events);
}