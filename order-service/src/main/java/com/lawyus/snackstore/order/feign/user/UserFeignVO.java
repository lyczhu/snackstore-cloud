package com.lawyus.snackstore.order.feign.user;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class UserFeignVO {

    private Long id;

    private String phone;

    private String nickname;

    private String avatar;

    private String role;

    private Integer status;
}
