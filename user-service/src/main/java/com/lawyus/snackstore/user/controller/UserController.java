package com.lawyus.snackstore.user.controller;

import com.lawyus.snackstore.common.response.PageResult;
import com.lawyus.snackstore.common.response.Result;
import com.lawyus.snackstore.user.model.dto.*;
import com.lawyus.snackstore.user.model.vo.LoginVO;
import com.lawyus.snackstore.user.model.vo.UserVO;
import com.lawyus.snackstore.user.service.UserService;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/user")
public class UserController {

    private final UserService userService;

    public UserController(UserService userService) {
        this.userService = userService;
    }

    @PostMapping("/register")
    public Result<LoginVO> register(@Valid @RequestBody UserRegisterDTO dto) {
        return Result.success(userService.register(dto));
    }

    @PostMapping("/login")
    public Result<LoginVO> login(@Valid @RequestBody UserLoginDTO dto) {
        return Result.success(userService.login(dto));
    }

    @PostMapping("/admin/login")
    public Result<LoginVO> adminLogin(@Valid @RequestBody AdminLoginDTO dto) {
        return Result.success(userService.adminLogin(dto));
    }

    @GetMapping("/{id}")
    public Result<UserVO> getUserById(@PathVariable Long id) {
        return Result.success(userService.getUserById(id));
    }

    @PutMapping("/{id}")
    public Result<UserVO> updateUser(@PathVariable Long id, @Valid @RequestBody UserUpdateDTO dto) {
        return Result.success(userService.updateUser(id, dto));
    }

    @GetMapping("/list")
    public Result<PageResult<UserVO>> getUserList(
            @RequestParam(defaultValue = "1") Integer pageNum,
            @RequestParam(defaultValue = "10") Integer pageSize) {
        return Result.success(userService.getUserList(pageNum, pageSize));
    }

    @PutMapping("/{id}/status")
    public Result<Void> updateUserStatus(@PathVariable Long id, @RequestParam Integer status) {
        userService.updateUserStatus(id, status);
        return Result.success(null);
    }
}
