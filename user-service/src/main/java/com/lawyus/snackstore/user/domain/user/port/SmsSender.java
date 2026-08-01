package com.lawyus.snackstore.user.domain.user.port;

/**
 * 短信发送通道端口，生产环境可替换为真实短信服务商实现
 */
public interface SmsSender {

    void send(String phone, String code);
}
