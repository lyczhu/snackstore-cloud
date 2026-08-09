package com.lawyus.snackstore.user.domain.user.port;

import java.time.Duration;

public interface VerificationCodePort {

    String get(String phone);

    void save(String phone, String code, Duration ttl);

    void invalidate(String phone);

    /**
     * 检查是否允许发送一次验证码（频率间隔 + 每日配额检查，不消耗配额）。
     * 通过后调用 {@link #markSendSuccess(String)} 计入每日配额。
     */
    boolean tryAcquireSend(String phone);

    /**
     * 验证码发送成功后累计每日发送配额（仅成功发放时计数）
     */
    void markSendSuccess(String phone);

    /**
     * 按客户端 IP 的发送限流（如每小时 N 次），成功返回 true
     */
    boolean tryAcquireSendByIp(String ip);

    /**
     * 校验侧失败锁定检查：失败次数达到上限返回 true
     */
    boolean isVerificationLocked(String phone);

    /**
     * 记录一次验证码校验失败，返回累计失败次数；达到上限时自动作废验证码
     */
    long incrementVerificationFail(String phone);

    /**
     * 验证码校验成功后清除失败计数
     */
    void resetVerificationFail(String phone);
}
