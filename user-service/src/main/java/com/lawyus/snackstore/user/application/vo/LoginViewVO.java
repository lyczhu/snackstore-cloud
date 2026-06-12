package com.lawyus.snackstore.user.application.vo;

import lombok.Getter;

@Getter
public class LoginViewVO {
    
    private final String token;
    private final UserViewVO user;
    
    public LoginViewVO(String token, UserViewVO user) {
        this.token = token;
        this.user = user;
    }
}
