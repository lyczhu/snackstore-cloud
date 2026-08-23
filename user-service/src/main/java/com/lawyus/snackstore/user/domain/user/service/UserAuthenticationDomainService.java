package com.lawyus.snackstore.user.domain.user.service;

import com.lawyus.snackstore.user.exception.BusinessExceptionEnum;
import com.lawyus.snackstore.user.domain.common.event.DomainEventPublisher;
import com.lawyus.snackstore.user.domain.user.model.entity.User;
import com.lawyus.snackstore.user.domain.user.model.valueobject.Password;
import com.lawyus.snackstore.user.domain.user.model.valueobject.Phone;
import com.lawyus.snackstore.user.domain.user.model.valueobject.UserRole;
import com.lawyus.snackstore.user.domain.user.port.LoginLockPort;
import com.lawyus.snackstore.user.domain.user.port.PasswordEncoder;
import com.lawyus.snackstore.user.domain.user.port.VerificationCodePort;
import com.lawyus.snackstore.user.domain.user.repository.UserRepository;

import lombok.extern.slf4j.Slf4j;

@Slf4j
public class UserAuthenticationDomainService {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final DomainEventPublisher eventPublisher;
    private final VerificationCodePort verificationCodePort;
    private final LoginLockPort loginLockPort;

    public UserAuthenticationDomainService(UserRepository userRepository,
                                           PasswordEncoder passwordEncoder,
                                           DomainEventPublisher eventPublisher,
                                           VerificationCodePort verificationCodePort,
                                           LoginLockPort loginLockPort) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
        this.eventPublisher = eventPublisher;
        this.verificationCodePort = verificationCodePort;
        this.loginLockPort = loginLockPort;
    }

    public User register(Phone phone, String rawPassword, String smsCode, String expectedCode) {
        if (verificationCodePort.isVerificationLocked(phone.getValue())) {
            throw BusinessExceptionEnum.SMS_CODE_ERROR.getException("验证码错误次数过多，请重新获取");
        }
        validateSmsCode(phone, smsCode, expectedCode);
        checkPhoneNotExists(phone);

        Password password = Password.fromRaw(rawPassword, passwordEncoder);
        User user = User.create(phone, password, UserRole.USER);

        User saved = userRepository.save(user);
        saved.onRegistered();
        eventPublisher.publishAll(saved.getDomainEvents());
        saved.clearDomainEvents();
        return saved;
    }

    public User login(Phone phone, String rawPassword) {
        if (loginLockPort.isLocked(phone.getValue())) {
            log.warn("登录已被锁定: phone={}", phone.getValue());
            throw BusinessExceptionEnum.PASSWORD_ERROR.getException("密码错误次数过多，请稍后重试");
        }

        User user = userRepository.findByPhone(phone)
                .orElseThrow(BusinessExceptionEnum.USER_NOT_FOUND::getException);

        if (!user.isActive()) {
            throw BusinessExceptionEnum.USER_DISABLED.getException();
        }

        if (!user.getPassword().matches(rawPassword, passwordEncoder)) {
            long failCount = loginLockPort.incrementFail(phone.getValue());
            log.warn("密码登录失败: phone={}, 累计失败次数={}", phone.getValue(), failCount);
            throw BusinessExceptionEnum.PASSWORD_ERROR.getException();
        }

        loginLockPort.resetFail(phone.getValue());
        user.onLogin();
        eventPublisher.publishAll(user.getDomainEvents());
        user.clearDomainEvents();
        return user;
    }

    public User adminLogin(Phone phone, String rawPassword) {
        User user = login(phone, rawPassword);
        if (!user.isAdmin()) {
            // 不提示"非管理员"，避免暴露该手机号为有效账号（用户枚举）
            log.warn("管理员登录被拒绝: 账号非管理员, phone={}", phone.getValue());
            throw BusinessExceptionEnum.USER_NOT_FOUND.getException("用户名或密码错误");
        }
        return user;
    }

    private void validateSmsCode(Phone phone, String smsCode, String expectedCode) {
        if (expectedCode == null) {
            throw BusinessExceptionEnum.SMS_CODE_EXPIRED.getException();
        }
        if (!expectedCode.equals(smsCode)) {
            long failCount = verificationCodePort.incrementVerificationFail(phone.getValue());
            log.warn("验证码校验失败: phone={}, 累计失败次数={}", phone.getValue(), failCount);
            throw BusinessExceptionEnum.SMS_CODE_ERROR.getException();
        }
    }

    private void checkPhoneNotExists(Phone phone) {
        if (userRepository.existsByPhone(phone)) {
            throw BusinessExceptionEnum.PHONE_ALREADY_EXISTS.getException();
        }
    }
}