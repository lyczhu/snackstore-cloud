package com.lawyus.snackstore.user.model.vo;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class UserVO {

    private Long id;

    private String phone;

    private String nickname;

    private String avatar;

    private String role;

    private Integer status;
}
