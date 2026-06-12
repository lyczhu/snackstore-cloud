package com.lawyus.snackstore.user.application.vo;

import lombok.Getter;

@Getter
public class UserViewVO {
    
    private final Long id;
    private final String phone;
    private final String nickname;
    private final String avatar;
    private final String role;
    private final Integer status;
    
    public UserViewVO(Long id, String phone, String nickname, String avatar, String role, Integer status) {
        this.id = id;
        this.phone = phone;
        this.nickname = nickname;
        this.avatar = avatar;
        this.role = role;
        this.status = status;
    }
}
