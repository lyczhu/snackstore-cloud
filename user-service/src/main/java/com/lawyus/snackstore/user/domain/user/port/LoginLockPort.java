package com.lawyus.snackstore.user.domain.user.port;

/**
 * 密码登录失败锁定端口：按手机号计数，达到上限后锁定一段时间，防止暴力破解。
 */
public interface LoginLockPort {

    /**
     * 该手机号当前是否处于登录锁定状态
     */
    boolean isLocked(String phone);

    /**
     * 记录一次密码登录失败，返回累计失败次数；达到上限后自动进入锁定窗口
     */
    long incrementFail(String phone);

    /**
     * 登录成功后清除失败计数
     */
    void resetFail(String phone);
}
