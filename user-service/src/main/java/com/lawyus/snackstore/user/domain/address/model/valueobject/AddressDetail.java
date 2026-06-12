package com.lawyus.snackstore.user.domain.address.model.valueobject;

import lombok.EqualsAndHashCode;
import lombok.Getter;

@Getter
@EqualsAndHashCode
public class AddressDetail {
    
    private final String province;
    private final String city;
    private final String district;
    private final String detail;
    
    private AddressDetail(String province, String city, String district, String detail) {
        if (detail == null || detail.isBlank()) {
            throw new IllegalArgumentException("详细地址不能为空");
        }
        this.province = province;
        this.city = city;
        this.district = district;
        this.detail = detail;
    }
    
    public static AddressDetail of(String province, String city, String district, String detail) {
        return new AddressDetail(province, city, district, detail);
    }
    
    public String getFullAddress() {
        StringBuilder sb = new StringBuilder();
        if (province != null) sb.append(province);
        if (city != null) sb.append(city);
        if (district != null) sb.append(district);
        sb.append(detail);
        return sb.toString();
    }
}
