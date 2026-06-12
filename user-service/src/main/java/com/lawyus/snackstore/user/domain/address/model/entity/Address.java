package com.lawyus.snackstore.user.domain.address.model.entity;

import com.lawyus.snackstore.user.domain.address.event.AddressCreatedEvent;
import com.lawyus.snackstore.user.domain.address.model.valueobject.AddressDetail;
import com.lawyus.snackstore.user.domain.address.model.valueobject.ReceiverInfo;
import com.lawyus.snackstore.user.domain.common.entity.AggregateRoot;
import lombok.Getter;

import java.time.LocalDateTime;

@Getter
public class Address extends AggregateRoot {

    private Long id;
    private Long userId;
    private ReceiverInfo receiverInfo;
    private AddressDetail addressDetail;
    private boolean isDefault;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    private Address() {
    }

    public static Address create(Long userId, ReceiverInfo receiverInfo, AddressDetail addressDetail, boolean isDefault) {
        if (userId == null) {
            throw new IllegalArgumentException("用户ID不能为空");
        }
        if (receiverInfo == null) {
            throw new IllegalArgumentException("收货人信息不能为空");
        }
        if (addressDetail == null) {
            throw new IllegalArgumentException("地址信息不能为空");
        }
        Address address = new Address();
        address.userId = userId;
        address.receiverInfo = receiverInfo;
        address.addressDetail = addressDetail;
        address.isDefault = isDefault;
        address.createdAt = LocalDateTime.now();
        address.updatedAt = LocalDateTime.now();
        return address;
    }

    public static Address restore(Long id, Long userId, ReceiverInfo receiverInfo,
                                  AddressDetail addressDetail, boolean isDefault,
                                  LocalDateTime createdAt, LocalDateTime updatedAt) {
        if (id == null || userId == null) {
            throw new IllegalArgumentException("地址基本信息不能为空");
        }
        Address address = new Address();
        address.id = id;
        address.userId = userId;
        address.receiverInfo = receiverInfo;
        address.addressDetail = addressDetail;
        address.isDefault = isDefault;
        address.createdAt = createdAt;
        address.updatedAt = updatedAt;
        return address;
    }

    public void updateInfo(ReceiverInfo receiverInfo, AddressDetail addressDetail, Boolean isDefault) {
        if (receiverInfo != null) {
            this.receiverInfo = receiverInfo;
        }
        if (addressDetail != null) {
            this.addressDetail = addressDetail;
        }
        if (isDefault != null) {
            this.isDefault = isDefault;
        }
        this.updatedAt = LocalDateTime.now();
    }

    public void setAsDefault() {
        this.isDefault = true;
        this.updatedAt = LocalDateTime.now();
    }

    public void unsetDefault() {
        this.isDefault = false;
        this.updatedAt = LocalDateTime.now();
    }

    public void onCreated() {
        registerEvent(new AddressCreatedEvent(id, userId, receiverInfo.getName()));
    }

    public boolean belongsToUser(Long userId) {
        return this.userId.equals(userId);
    }

    public void assignId(Long id) {
        this.id = id;
    }
}