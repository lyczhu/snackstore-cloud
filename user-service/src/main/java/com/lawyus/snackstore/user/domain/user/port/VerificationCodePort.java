package com.lawyus.snackstore.user.domain.user.port;

import java.time.Duration;

public interface VerificationCodePort {

    String get(String phone);

    void save(String phone, String code, Duration ttl);

    void invalidate(String phone);

    /**
     * 尝试获取一次验证码发送配额（频率限流），成功返回 true
     */
    boolean tryAcquireSend(String phone);
}
