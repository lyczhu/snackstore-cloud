package com.lawyus.snackstore.user.infrastructure.event;

import com.lawyus.snackstore.user.domain.common.event.BaseDomainEvent;
import com.lawyus.snackstore.user.domain.common.event.DomainEventPublisher;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
@Slf4j
public class SpringDomainEventPublisher implements DomainEventPublisher {

    private final ApplicationEventPublisher applicationEventPublisher;

    public SpringDomainEventPublisher(ApplicationEventPublisher applicationEventPublisher) {
        this.applicationEventPublisher = applicationEventPublisher;
    }

    @Override
    public void publish(BaseDomainEvent event) {
        log.debug("Publishing domain event: {}", event.getEventId());
        applicationEventPublisher.publishEvent(event);
    }

    @Override
    public void publishAll(List<BaseDomainEvent> events) {
        events.forEach(this::publish);
    }
}