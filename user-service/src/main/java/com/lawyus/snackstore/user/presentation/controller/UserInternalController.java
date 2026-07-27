package com.lawyus.snackstore.user.presentation.controller;

import com.lawyus.snackstore.common.response.Result;
import com.lawyus.snackstore.user.application.service.UserApplicationService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/internal/users")
public class UserInternalController {

    private final UserApplicationService userApplicationService;

    public UserInternalController(UserApplicationService userApplicationService) {
        this.userApplicationService = userApplicationService;
    }

    @GetMapping("/count")
    public Result<Long> countUsers() {
        return Result.success(userApplicationService.countUsers());
    }
}
