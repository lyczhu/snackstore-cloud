package com.lawyus.snackstore.user.presentation.controller;

import com.lawyus.snackstore.common.response.PageResult;
import com.lawyus.snackstore.common.response.Result;
import com.lawyus.snackstore.user.application.dto.UserUpdateCommand;
import com.lawyus.snackstore.user.application.service.UserApplicationService;
import com.lawyus.snackstore.user.application.vo.UserViewVO;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/admin/users")
public class AdminUserController {

    private final UserApplicationService userApplicationService;

    public AdminUserController(UserApplicationService userApplicationService) {
        this.userApplicationService = userApplicationService;
    }

    @GetMapping("/{id}")
    public Result<UserViewVO> getUserById(@PathVariable Long id) {
        return Result.success(userApplicationService.getUserById(id));
    }

    @PutMapping("/{id}")
    public Result<UserViewVO> updateUser(@PathVariable Long id, @Valid @RequestBody UserUpdateCommand command) {
        return Result.success(userApplicationService.updateUser(id, command));
    }

    @GetMapping
    public Result<PageResult<UserViewVO>> getUserList(
            @RequestParam(defaultValue = "1") Integer pageNum,
            @RequestParam(defaultValue = "10") Integer pageSize) {
        return Result.success(userApplicationService.getUserList(pageNum, pageSize));
    }

    @PatchMapping("/{id}/status")
    public Result<Void> updateUserStatus(@PathVariable Long id, @RequestParam Integer status) {
        userApplicationService.updateUserStatus(id, status);
        return Result.success(null);
    }
}
