package com.lawyus.snackstore.user.presentation.controller;

import com.lawyus.snackstore.common.response.Result;
import com.lawyus.snackstore.user.application.dto.SmsCodeSendCommand;
import com.lawyus.snackstore.user.application.dto.UserRegisterCommand;
import com.lawyus.snackstore.user.application.dto.UserLoginCommand;
import com.lawyus.snackstore.user.application.service.UserApplicationService;
import com.lawyus.snackstore.user.application.vo.LoginViewVO;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/auth")
public class AuthController {

    private final UserApplicationService userApplicationService;

    public AuthController(UserApplicationService userApplicationService) {
        this.userApplicationService = userApplicationService;
    }

    @PostMapping("/sms-code")
    public Result<Void> sendSmsCode(@Valid @RequestBody SmsCodeSendCommand command) {
        userApplicationService.sendSmsCode(command.getPhone());
        return Result.success(null);
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
    public Result<Void> logout(@RequestHeader("X-User-Id") Long userId) {
        userApplicationService.logout(userId);
        return Result.success(null);
    }
}
