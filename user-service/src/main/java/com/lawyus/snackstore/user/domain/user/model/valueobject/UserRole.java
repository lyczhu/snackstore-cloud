package com.lawyus.snackstore.user.domain.user.model.valueobject;

import lombok.Getter;

@Getter
public enum UserRole {
    
    USER("USER", "普通用户"),
    ADMIN("ADMIN", "管理员");
    
    private final String code;
    private final String description;
    
    UserRole(String code, String description) {
        this.code = code;
        this.description = description;
    }
    
    public static UserRole fromCode(String code) {
        for (UserRole role : values()) {
            if (role.code.equals(code)) {
                return role;
            }
        }
        throw new IllegalArgumentException("无效的用户角色: " + code);
    }
    
    @Override
    public String toString() {
        return code;
    }
}
