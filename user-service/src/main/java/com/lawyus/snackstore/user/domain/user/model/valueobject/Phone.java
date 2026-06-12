package com.lawyus.snackstore.user.domain.user.model.valueobject;

import jakarta.validation.constraints.Pattern;
import lombok.EqualsAndHashCode;
import lombok.Getter;

@Getter
@EqualsAndHashCode
public class Phone {
    
    private static final String PHONE_PATTERN = "^1[3-9]\\d{9}$";
    
    private final String value;
    
    private Phone(String value) {
        if (value == null || !value.matches(PHONE_PATTERN)) {
            throw new IllegalArgumentException("手机号格式不正确");
        }
        this.value = value;
    }
    
    public static Phone of(String value) {
        return new Phone(value);
    }
    
    @Override
    public String toString() {
        return value;
    }
}
