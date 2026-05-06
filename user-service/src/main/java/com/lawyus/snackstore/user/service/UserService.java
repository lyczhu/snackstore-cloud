package com.lawyus.snackstore.user.service;

import com.lawyus.snackstore.common.response.PageResult;
import com.lawyus.snackstore.user.model.dto.*;
import com.lawyus.snackstore.user.model.vo.LoginVO;
import com.lawyus.snackstore.user.model.vo.UserVO;

public interface UserService {

    LoginVO register(UserRegisterDTO dto);

    LoginVO login(UserLoginDTO dto);

    LoginVO adminLogin(AdminLoginDTO dto);

    UserVO getUserById(Long id);

    UserVO updateUser(Long id, UserUpdateDTO dto);

    PageResult<UserVO> getUserList(Integer pageNum, Integer pageSize);

    void updateUserStatus(Long id, Integer status);
}
