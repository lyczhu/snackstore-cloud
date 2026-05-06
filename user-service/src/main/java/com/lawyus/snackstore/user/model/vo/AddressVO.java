package com.lawyus.snackstore.user.model.vo;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class AddressVO {

    private Long id;

    private Long userId;

    private String receiverName;

    private String receiverPhone;

    private String province;

    private String city;

    private String district;

    private String detail;

    private Integer isDefault;
}
