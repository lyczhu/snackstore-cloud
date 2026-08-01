package com.lawyus.snackstore.user.infrastructure.port;

import com.lawyus.snackstore.user.domain.user.port.SmsSender;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * 开发环境短信通道：验证码打印到日志。
 * 生产环境请实现真实短信服务商（如阿里云短信）替换该 Bean。
 */
@Component
public class ConsoleSmsSender implements SmsSender {

    private static final Logger log = LoggerFactory.getLogger(ConsoleSmsSender.class);

    @Override
    public void send(String phone, String code) {
        log.info("【模拟短信】手机号: {}, 验证码: {}, 5分钟内有效", phone, code);
    }
}
