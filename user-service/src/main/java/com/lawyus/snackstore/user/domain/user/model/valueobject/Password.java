package com.lawyus.snackstore.user.domain.user.model.valueobject;

import com.lawyus.snackstore.user.domain.user.port.PasswordEncoder;
import lombok.EqualsAndHashCode;
import lombok.Getter;

import java.nio.charset.StandardCharsets;

@Getter
@EqualsAndHashCode
public class Password {

    private static final int BCRYPT_MAX_BYTES = 72;

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
        if (rawValue.getBytes(StandardCharsets.UTF_8).length > BCRYPT_MAX_BYTES) {
            throw new IllegalArgumentException("密码长度不能超过72字节(BCrypt限制)");
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
        return "********";
    }
}