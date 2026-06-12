package com.lawyus.snackstore.user.domain.user.event;

import com.lawyus.snackstore.user.domain.common.event.BaseDomainEvent;
import lombok.Getter;

@Getter
public class UserRegisteredEvent extends BaseDomainEvent {

    private final Long userId;
    private final String phone;
    private final String nickname;

    public UserRegisteredEvent(Long userId, String phone, String nickname) {
        this.userId = userId;
        this.phone = phone;
        this.nickname = nickname;
    }
}