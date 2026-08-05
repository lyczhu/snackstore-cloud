package com.lawyus.snackstore.user.application.service;

import com.lawyus.snackstore.common.response.PageResult;
import com.lawyus.snackstore.user.application.dto.UserRegisterCommand;
import com.lawyus.snackstore.user.application.dto.UserLoginCommand;
import com.lawyus.snackstore.user.application.dto.UserUpdateCommand;
import com.lawyus.snackstore.user.application.vo.LoginViewVO;
import com.lawyus.snackstore.user.application.vo.UserViewVO;

public interface UserApplicationService {

    LoginViewVO register(UserRegisterCommand command);

    void sendSmsCode(String phone);

    LoginViewVO login(UserLoginCommand command);

    LoginViewVO adminLogin(UserLoginCommand command);

    void logout(Long userId);

    UserViewVO getUserById(Long id);

    UserViewVO updateUser(Long id, UserUpdateCommand command);

    PageResult<UserViewVO> getUserList(Integer pageNum, Integer pageSize);

    long countUsers();

    void updateUserStatus(Long id, Integer status);
}