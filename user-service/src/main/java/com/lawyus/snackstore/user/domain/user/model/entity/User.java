package com.lawyus.snackstore.user.domain.user.model.entity;

import com.lawyus.snackstore.user.exception.BusinessExceptionEnum;
import com.lawyus.snackstore.user.domain.common.entity.AggregateRoot;
import com.lawyus.snackstore.user.domain.user.event.UserLoginEvent;
import com.lawyus.snackstore.user.domain.user.event.UserRegisteredEvent;
import com.lawyus.snackstore.user.domain.user.model.valueobject.Password;
import com.lawyus.snackstore.user.domain.user.model.valueobject.Phone;
import com.lawyus.snackstore.user.domain.user.model.valueobject.UserRole;
import com.lawyus.snackstore.user.domain.user.model.valueobject.UserStatus;
import lombok.Getter;

import java.time.LocalDateTime;

@Getter
public class User extends AggregateRoot {

    private Long id;
    private Phone phone;
    private Password password;
    private String nickname;
    private String avatar;
    private UserRole role;
    private UserStatus status;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    private User() {
    }

    public static User create(Phone phone, Password password, UserRole role) {
        if (phone == null || password == null || role == null) {
            throw new IllegalArgumentException("用户基本信息不能为空");
        }
        User user = new User();
        user.phone = phone;
        user.password = password;
        user.role = role;
        user.status = UserStatus.ENABLED;
        user.nickname = generateDefaultNickname(phone);
        user.createdAt = LocalDateTime.now();
        user.updatedAt = LocalDateTime.now();
        return user;
    }

    public static User restore(Long id, Phone phone, Password password, String nickname,
                               String avatar, UserRole role, UserStatus status,
                               LocalDateTime createdAt, LocalDateTime updatedAt) {
        if (id == null || phone == null || password == null) {
            throw new IllegalArgumentException("用户基本信息不能为空");
        }
        User user = new User();
        user.id = id;
        user.phone = phone;
        user.password = password;
        user.nickname = nickname;
        user.avatar = avatar;
        user.role = role;
        user.status = status;
        user.createdAt = createdAt;
        user.updatedAt = updatedAt;
        return user;
    }

    public void updateProfile(String nickname, String avatar, Phone phone) {
        if (nickname != null) {
            this.nickname = nickname;
        }
        if (avatar != null) {
            this.avatar = avatar;
        }
        if (phone != null) {
            this.phone = phone;
        }
        this.updatedAt = LocalDateTime.now();
    }

    public void disable() {
        if (this.status == UserStatus.DISABLED) {
            throw BusinessExceptionEnum.USER_ALREADY_DISABLED.getException();
        }
        this.status = UserStatus.DISABLED;
        this.updatedAt = LocalDateTime.now();
    }

    public void enable() {
        if (this.status == UserStatus.ENABLED) {
            throw BusinessExceptionEnum.USER_ALREADY_ENABLED.getException();
        }
        this.status = UserStatus.ENABLED;
        this.updatedAt = LocalDateTime.now();
    }

    public void onRegistered() {
        registerEvent(new UserRegisteredEvent(id, phone.getValue(), nickname));
    }

    public void onLogin() {
        registerEvent(new UserLoginEvent(id, phone.getValue(), role.getCode()));
    }

    public boolean isAdmin() {
        return UserRole.ADMIN.equals(this.role);
    }

    public boolean isActive() {
        return UserStatus.ENABLED.equals(this.status);
    }

    public void assignId(Long id) {
        this.id = id;
    }

    private static String generateDefaultNickname(Phone phone) {
        String phoneValue = phone.getValue();
        if (phoneValue.length() < 8) {
            return "用户" + phoneValue;
        }
        return "用户" + phoneValue.substring(phoneValue.length() - 4);
    }
}