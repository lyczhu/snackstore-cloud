package com.lawyus.snackstore.user.domain.user.model.valueobject;

import lombok.Getter;

@Getter
public enum UserStatus {
    
    DISABLED(0, "禁用"),
    ENABLED(1, "启用");
    
    private final int code;
    private final String description;
    
    UserStatus(int code, String description) {
        this.code = code;
        this.description = description;
    }
    
    public static UserStatus fromCode(int code) {
        for (UserStatus status : values()) {
            if (status.code == code) {
                return status;
            }
        }
        throw new IllegalArgumentException("无效的用户状态: " + code);
    }
    
    public boolean isEnabled() {
        return this == ENABLED;
    }
    
    @Override
    public String toString() {
        return code + "(" + description + ")";
    }
}
