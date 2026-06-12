package com.lawyus.snackstore.user.domain.user.event;

import com.lawyus.snackstore.user.domain.common.event.BaseDomainEvent;
import lombok.Getter;

@Getter
public class UserLoginEvent extends BaseDomainEvent {

    private final Long userId;
    private final String phone;
    private final String role;

    public UserLoginEvent(Long userId, String phone, String role) {
        this.userId = userId;
        this.phone = phone;
        this.role = role;
    }
}