package com.lawyus.snackstore.user.domain.address.model.valueobject;

import com.lawyus.snackstore.user.domain.user.model.valueobject.Phone;
import lombok.EqualsAndHashCode;
import lombok.Getter;

@Getter
@EqualsAndHashCode
public class ReceiverInfo {

    private final String name;
    private final Phone phone;

    private ReceiverInfo(String name, Phone phone) {
        if (name == null || name.isBlank()) {
            throw new IllegalArgumentException("收货人姓名不能为空");
        }
        if (phone == null) {
            throw new IllegalArgumentException("收货人手机号不能为空");
        }
        this.name = name;
        this.phone = phone;
    }

    public static ReceiverInfo of(String name, String phoneValue) {
        return new ReceiverInfo(name, Phone.of(phoneValue));
    }
}