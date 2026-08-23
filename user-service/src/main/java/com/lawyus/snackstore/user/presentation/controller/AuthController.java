package com.lawyus.snackstore.user.presentation.controller;

import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.lawyus.snackstore.common.constant.AuthConstants;
import com.lawyus.snackstore.common.response.Result;
import com.lawyus.snackstore.user.application.dto.SmsCodeSendCommand;
import com.lawyus.snackstore.user.application.dto.UserLoginCommand;
import com.lawyus.snackstore.user.application.dto.UserRegisterCommand;
import com.lawyus.snackstore.user.application.service.UserApplicationService;
import com.lawyus.snackstore.user.application.vo.LoginViewVO;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;

@RestController
@RequestMapping("/auth")
public class AuthController {

    private final UserApplicationService userApplicationService;

    public AuthController(UserApplicationService userApplicationService) {
        this.userApplicationService = userApplicationService;
    }

    @PostMapping("/sms-code")
    public Result<Void> sendSmsCode(@Valid @RequestBody SmsCodeSendCommand command,
                                    HttpServletRequest request) {
        userApplicationService.sendSmsCode(command.getPhone(), extractClientIp(request));
        return Result.success(null);
    }

    /**
     * 获取客户端 IP：优先取 X-Real-IP（由网关按 TCP 连接对端地址覆写，不可伪造），
     * 其次取 X-Forwarded-For 最后一跳（最近一跳代理追加），兜底 RemoteAddr
     */
    private String extractClientIp(HttpServletRequest request) {
        String realIp = request.getHeader("X-Real-IP");
        if (realIp != null && !realIp.isBlank()) {
            return realIp.trim();
        }
        String forwarded = request.getHeader("X-Forwarded-For");
        if (forwarded != null && !forwarded.isBlank()) {
            int comma = forwarded.lastIndexOf(',');
            return (comma > 0 ? forwarded.substring(comma + 1) : forwarded).trim();
        }
        return request.getRemoteAddr();
    }

    @PostMapping("/register")
    public Result<LoginViewVO> register(@Valid @RequestBody UserRegisterCommand command) {
        return Result.success(userApplicationService.register(command));
    }

    @PostMapping("/login")
    public Result<LoginViewVO> login(@Valid @RequestBody UserLoginCommand command) {
        return Result.success(userApplicationService.login(command));
    }

    @PostMapping("/admin/login")
    public Result<LoginViewVO> adminLogin(@Valid @RequestBody UserLoginCommand command) {
        return Result.success(userApplicationService.adminLogin(command));
    }

    @PostMapping("/logout")
    public Result<Void> logout(@RequestHeader(AuthConstants.HEADER_USER_ID) Long userId) {
        userApplicationService.logout(userId);
        return Result.success(null);
    }
}
