package com.lawyus.snackstore.user.domain.common.event;

import java.time.LocalDateTime;
import java.util.UUID;

public abstract class BaseDomainEvent {

    private final String eventId;
    private final LocalDateTime occurredAt;

    protected BaseDomainEvent() {
        this.eventId = UUID.randomUUID().toString();
        this.occurredAt = LocalDateTime.now();
    }

    public String getEventId() {
        return eventId;
    }

    public LocalDateTime getOccurredAt() {
        return occurredAt;
    }
}