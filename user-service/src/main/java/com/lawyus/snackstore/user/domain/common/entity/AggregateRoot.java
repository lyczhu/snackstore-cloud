package com.lawyus.snackstore.user.domain.common.entity;

import com.lawyus.snackstore.user.domain.common.event.BaseDomainEvent;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public abstract class AggregateRoot {

    private final List<BaseDomainEvent> domainEvents = new ArrayList<>();

    protected void registerEvent(BaseDomainEvent event) {
        domainEvents.add(event);
    }

    public List<BaseDomainEvent> getDomainEvents() {
        return Collections.unmodifiableList(domainEvents);
    }

    public void clearDomainEvents() {
        domainEvents.clear();
    }
}