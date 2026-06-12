package com.lawyus.snackstore.user.application.vo;

import lombok.Getter;

@Getter
public class AddressViewVO {
    
    private final Long id;
    private final Long userId;
    private final String receiverName;
    private final String receiverPhone;
    private final String province;
    private final String city;
    private final String district;
    private final String detail;
    private final Boolean isDefault;
    
    public AddressViewVO(Long id, Long userId, String receiverName, String receiverPhone,
                         String province, String city, String district, String detail, Boolean isDefault) {
        this.id = id;
        this.userId = userId;
        this.receiverName = receiverName;
        this.receiverPhone = receiverPhone;
        this.province = province;
        this.city = city;
        this.district = district;
        this.detail = detail;
        this.isDefault = isDefault;
    }
}
