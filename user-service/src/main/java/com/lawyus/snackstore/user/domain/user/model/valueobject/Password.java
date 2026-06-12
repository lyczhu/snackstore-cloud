package com.lawyus.snackstore.user.domain.user.model.valueobject;

import com.lawyus.snackstore.user.domain.user.port.PasswordEncoder;
import lombok.EqualsAndHashCode;
import lombok.Getter;

@Getter
@EqualsAndHashCode
public class Password {

    private final String encodedValue;

    private Password(String encodedValue) {
        if (encodedValue == null || encodedValue.isBlank()) {
            throw new IllegalArgumentException("密码不能为空");
        }
        this.encodedValue = encodedValue;
    }

    public static Password fromRaw(String rawValue, PasswordEncoder encoder) {
        if (rawValue == null || rawValue.isBlank()) {
            throw new IllegalArgumentException("密码不能为空");
        }
        return new Password(encoder.encode(rawValue));
    }

    public static Password fromEncoded(String encodedValue) {
        return new Password(encodedValue);
    }

    public boolean matches(String rawPassword, PasswordEncoder encoder) {
        return encoder.matches(rawPassword, encodedValue);
    }

    @Override
    public String toString() {
        return encodedValue;
    }
}