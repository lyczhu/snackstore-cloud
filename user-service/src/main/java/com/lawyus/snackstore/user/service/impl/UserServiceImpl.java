package com.lawyus.snackstore.user.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.lawyus.snackstore.common.exception.BusinessException;
import com.lawyus.snackstore.common.exception.BusinessExceptionEnum;
import com.lawyus.snackstore.common.response.PageResult;
import com.lawyus.snackstore.common.util.JwtUtil;
import com.lawyus.snackstore.user.mapper.UserMapper;
import com.lawyus.snackstore.user.model.dto.*;
import com.lawyus.snackstore.user.model.entity.User;
import com.lawyus.snackstore.user.model.vo.LoginVO;
import com.lawyus.snackstore.user.model.vo.UserVO;
import com.lawyus.snackstore.user.service.UserService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.concurrent.TimeUnit;

@Service
@Slf4j
public class UserServiceImpl implements UserService {

    private static final String SMS_CODE_PREFIX = "sms:code:";
    private static final BCryptPasswordEncoder PASSWORD_ENCODER = new BCryptPasswordEncoder();

    private final UserMapper userMapper;
    private final StringRedisTemplate redisTemplate;

    public UserServiceImpl(UserMapper userMapper, StringRedisTemplate redisTemplate) {
        this.userMapper = userMapper;
        this.redisTemplate = redisTemplate;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public LoginVO register(UserRegisterDTO dto) {
        String codeKey = SMS_CODE_PREFIX + dto.getPhone();
        String cachedCode = redisTemplate.opsForValue().get(codeKey);
        if (cachedCode == null || !cachedCode.equals(dto.getCode())) {
            throw BusinessExceptionEnum.SMS_CODE_ERROR.getException();
        }

        Long count = userMapper.selectCount(
                new LambdaQueryWrapper<User>().eq(User::getPhone, dto.getPhone()));
        if (count > 0) {
            throw BusinessExceptionEnum.PHONE_ALREADY_EXISTS.getException();
        }

        User user = new User();
        user.setPhone(dto.getPhone());
        user.setPassword(PASSWORD_ENCODER.encode(dto.getPassword()));
        user.setNickname("用户" + dto.getPhone().substring(7));
        user.setRole("USER");
        user.setStatus(1);
        userMapper.insert(user);

        redisTemplate.delete(codeKey);

        return buildLoginVO(user);
    }

    @Override
    public LoginVO login(UserLoginDTO dto) {
        User user = userMapper.selectOne(
                new LambdaQueryWrapper<User>().eq(User::getPhone, dto.getPhone()));
        if (user == null) {
            throw BusinessExceptionEnum.USER_NOT_FOUND.getException();
        }
        if (user.getStatus() == 0) {
            throw BusinessExceptionEnum.USER_DISABLED.getException();
        }
        if (!PASSWORD_ENCODER.matches(dto.getPassword(), user.getPassword())) {
            throw BusinessExceptionEnum.PASSWORD_ERROR.getException();
        }
        return buildLoginVO(user);
    }

    @Override
    public LoginVO adminLogin(AdminLoginDTO dto) {
        User user = userMapper.selectOne(
                new LambdaQueryWrapper<User>().eq(User::getPhone, dto.getPhone()));
        if (user == null) {
            throw BusinessExceptionEnum.USER_NOT_FOUND.getException();
        }
        if (!"ADMIN".equals(user.getRole())) {
            throw BusinessExceptionEnum.USER_NOT_FOUND.getException("非管理员账号");
        }
        if (!PASSWORD_ENCODER.matches(dto.getPassword(), user.getPassword())) {
            throw BusinessExceptionEnum.PASSWORD_ERROR.getException();
        }
        return buildLoginVO(user);
    }

    @Override
    public UserVO getUserById(Long id) {
        User user = userMapper.selectById(id);
        if (user == null) {
            throw BusinessExceptionEnum.USER_NOT_FOUND.getException();
        }
        return convertToVO(user);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public UserVO updateUser(Long id, UserUpdateDTO dto) {
        User user = userMapper.selectById(id);
        if (user == null) {
            throw BusinessExceptionEnum.USER_NOT_FOUND.getException();
        }
        if (dto.getNickname() != null) {
            user.setNickname(dto.getNickname());
        }
        if (dto.getAvatar() != null) {
            user.setAvatar(dto.getAvatar());
        }
        if (dto.getPhone() != null) {
            user.setPhone(dto.getPhone());
        }
        userMapper.updateById(user);
        return convertToVO(user);
    }

    @Override
    public PageResult<UserVO> getUserList(Integer pageNum, Integer pageSize) {
        Page<User> page = new Page<>(pageNum, pageSize);
        Page<User> result = userMapper.selectPage(page,
                new LambdaQueryWrapper<User>().orderByDesc(User::getCreatedAt));
        return PageResult.success(
                result.getRecords().stream().map(this::convertToVO).toList(),
                result.getCurrent(), result.getSize(), result.getTotal());
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void updateUserStatus(Long id, Integer status) {
        User user = userMapper.selectById(id);
        if (user == null) {
            throw BusinessExceptionEnum.USER_NOT_FOUND.getException();
        }
        user.setStatus(status);
        userMapper.updateById(user);
    }

    private LoginVO buildLoginVO(User user) {
        String token = JwtUtil.generateToken(user.getId(), user.getPhone(), user.getRole());
        redisTemplate.opsForValue().set("token:" + user.getId(), token, 2, TimeUnit.HOURS);

        LoginVO loginVO = new LoginVO();
        loginVO.setToken(token);
        loginVO.setUser(convertToVO(user));
        return loginVO;
    }

    private UserVO convertToVO(User user) {
        UserVO vo = new UserVO();
        vo.setId(user.getId());
        vo.setPhone(user.getPhone());
        vo.setNickname(user.getNickname());
        vo.setAvatar(user.getAvatar());
        vo.setRole(user.getRole());
        vo.setStatus(user.getStatus());
        return vo;
    }
}
