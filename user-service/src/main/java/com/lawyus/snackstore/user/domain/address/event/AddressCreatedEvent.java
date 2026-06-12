package com.lawyus.snackstore.user.domain.address.event;

import com.lawyus.snackstore.user.domain.common.event.BaseDomainEvent;
import lombok.Getter;

@Getter
public class AddressCreatedEvent extends BaseDomainEvent {

    private final Long addressId;
    private final Long userId;
    private final String receiverName;

    public AddressCreatedEvent(Long addressId, Long userId, String receiverName) {
        this.addressId = addressId;
        this.userId = userId;
        this.receiverName = receiverName;
    }
}