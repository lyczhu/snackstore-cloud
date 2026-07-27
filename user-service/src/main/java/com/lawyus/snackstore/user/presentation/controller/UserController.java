package com.lawyus.snackstore.user.presentation.controller;

import com.lawyus.snackstore.common.response.PageResult;
import com.lawyus.snackstore.common.response.Result;
import com.lawyus.snackstore.user.application.dto.UserUpdateCommand;
import com.lawyus.snackstore.user.application.service.UserApplicationService;
import com.lawyus.snackstore.user.application.vo.UserViewVO;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/users")
public class UserController {
    
    private final UserApplicationService userApplicationService;
    
    public UserController(UserApplicationService userApplicationService) {
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
