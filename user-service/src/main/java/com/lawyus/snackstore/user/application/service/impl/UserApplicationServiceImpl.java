package com.lawyus.snackstore.user.application.service.impl;

import java.util.List;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.lawyus.snackstore.common.response.PageResult;
import com.lawyus.snackstore.user.application.converter.UserViewConverter;
import com.lawyus.snackstore.user.application.dto.UserLoginCommand;
import com.lawyus.snackstore.user.application.dto.UserRegisterCommand;
import com.lawyus.snackstore.user.application.dto.UserUpdateCommand;
import com.lawyus.snackstore.user.application.service.UserApplicationService;
import com.lawyus.snackstore.user.application.vo.LoginViewVO;
import com.lawyus.snackstore.user.application.vo.UserViewVO;
import com.lawyus.snackstore.user.domain.common.model.PagedResult;
import com.lawyus.snackstore.user.domain.user.model.entity.User;
import com.lawyus.snackstore.user.domain.user.model.valueobject.Phone;
import com.lawyus.snackstore.user.domain.user.model.valueobject.UserStatus;
import com.lawyus.snackstore.user.domain.user.port.TokenPort;
import com.lawyus.snackstore.user.domain.user.port.VerificationCodePort;
import com.lawyus.snackstore.user.domain.user.service.UserAuthenticationDomainService;
import com.lawyus.snackstore.user.domain.user.service.UserManagementDomainService;

import lombok.extern.slf4j.Slf4j;

@Service
@Slf4j
public class UserApplicationServiceImpl implements UserApplicationService {

    private final UserAuthenticationDomainService userAuthenticationDomainService;
    private final UserManagementDomainService userManagementDomainService;
    private final VerificationCodePort verificationCodePort;
    private final TokenPort tokenPort;

    public UserApplicationServiceImpl(UserAuthenticationDomainService userAuthenticationDomainService,
                                      UserManagementDomainService userManagementDomainService,
                                      VerificationCodePort verificationCodePort,
                                      TokenPort tokenPort) {
        this.userAuthenticationDomainService = userAuthenticationDomainService;
        this.userManagementDomainService = userManagementDomainService;
        this.verificationCodePort = verificationCodePort;
        this.tokenPort = tokenPort;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public LoginViewVO register(UserRegisterCommand command) {
        String cachedCode = verificationCodePort.get(command.getPhone());

        Phone phone = Phone.of(command.getPhone());
        User user = userAuthenticationDomainService.register(phone, command.getPassword(),
                command.getCode(), cachedCode);

        verificationCodePort.invalidate(command.getPhone());
        return buildLoginViewVO(user);
    }

    @Override
    public LoginViewVO login(UserLoginCommand command) {
        Phone phone = Phone.of(command.getPhone());
        User user = userAuthenticationDomainService.login(phone, command.getPassword());
        return buildLoginViewVO(user);
    }

    @Override
    public LoginViewVO adminLogin(UserLoginCommand command) {
        Phone phone = Phone.of(command.getPhone());
        User user = userAuthenticationDomainService.adminLogin(phone, command.getPassword());
        return buildLoginViewVO(user);
    }

    @Override
    public UserViewVO getUserById(Long id) {
        User user = userManagementDomainService.getUserById(id);
        return UserViewConverter.toViewVO(user);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public UserViewVO updateUser(Long id, UserUpdateCommand command) {
        Phone phone = command.getPhone() != null ? Phone.of(command.getPhone()) : null;
        User user = userManagementDomainService.updateUser(id, command.getNickname(), command.getAvatar(), phone);
        return UserViewConverter.toViewVO(user);
    }

    @Override
    public PageResult<UserViewVO> getUserList(Integer pageNum, Integer pageSize) {
        int pn = pageNum != null ? pageNum : 1;
        int ps = pageSize != null ? pageSize : 10;
        PagedResult<User> pagedResult = userManagementDomainService.getUserList(pn, ps);

        List<UserViewVO> userVOs = pagedResult.content().stream()
                .map(UserViewConverter::toViewVO)
                .toList();

        return PageResult.success(userVOs, (long) pn, (long) ps, pagedResult.total());
    }

    @Override
    public long countUsers() {
        return userManagementDomainService.count();
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void updateUserStatus(Long id, Integer status) {
        UserStatus userStatus = UserStatus.fromCode(status);
        userManagementDomainService.changeUserStatus(id, userStatus);
    }

    private LoginViewVO buildLoginViewVO(User user) {
        String token = tokenPort.generate(user.getId(), user.getPhone().getValue(), user.getRole().getCode());
        tokenPort.store(user.getId(), token);

        UserViewVO userVO = UserViewConverter.toViewVO(user);
        return new LoginViewVO(token, userVO);
    }
}