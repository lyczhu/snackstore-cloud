package com.lawyus.snackstore.user.model.vo;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class LoginVO {

    private String token;

    private UserVO user;
}
