package com.lawyus.snackstore.product.model.event;

public record ProductChangedEvent(Long productId, ChangeType type) {

    public enum ChangeType {
        CREATED,
        UPDATED,
        DELETED,
        STATUS_CHANGED,
        STOCK_CHANGED
    }
}