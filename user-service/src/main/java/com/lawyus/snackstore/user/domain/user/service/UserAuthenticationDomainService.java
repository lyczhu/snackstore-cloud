package com.lawyus.snackstore.user.domain.user.service;

import com.lawyus.snackstore.user.exception.BusinessExceptionEnum;
import com.lawyus.snackstore.user.domain.common.event.DomainEventPublisher;
import com.lawyus.snackstore.user.domain.user.model.entity.User;
import com.lawyus.snackstore.user.domain.user.model.valueobject.Password;
import com.lawyus.snackstore.user.domain.user.model.valueobject.Phone;
import com.lawyus.snackstore.user.domain.user.model.valueobject.UserRole;
import com.lawyus.snackstore.user.domain.user.port.PasswordEncoder;
import com.lawyus.snackstore.user.domain.user.repository.UserRepository;

import lombok.extern.slf4j.Slf4j;

@Slf4j
public class UserAuthenticationDomainService {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final DomainEventPublisher eventPublisher;

    public UserAuthenticationDomainService(UserRepository userRepository,
                                           PasswordEncoder passwordEncoder,
                                           DomainEventPublisher eventPublisher) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
        this.eventPublisher = eventPublisher;
    }

    public User register(Phone phone, String rawPassword, String smsCode, String expectedCode) {
        validateSmsCode(smsCode, expectedCode);
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
        User user = userRepository.findByPhone(phone)
                .orElseThrow(BusinessExceptionEnum.USER_NOT_FOUND::getException);

        if (!user.isActive()) {
            throw BusinessExceptionEnum.USER_DISABLED.getException();
        }

        if (!user.getPassword().matches(rawPassword, passwordEncoder)) {
            throw BusinessExceptionEnum.PASSWORD_ERROR.getException();
        }

        user.onLogin();
        eventPublisher.publishAll(user.getDomainEvents());
        user.clearDomainEvents();
        return user;
    }

    public User adminLogin(Phone phone, String rawPassword) {
        User user = login(phone, rawPassword);
        if (!user.isAdmin()) {
            throw BusinessExceptionEnum.USER_NOT_FOUND.getException("非管理员账号");
        }
        return user;
    }

    private void validateSmsCode(String smsCode, String expectedCode) {
        if (expectedCode == null) {
            throw BusinessExceptionEnum.SMS_CODE_EXPIRED.getException();
        }
        if (!expectedCode.equals(smsCode)) {
            throw BusinessExceptionEnum.SMS_CODE_ERROR.getException();
        }
    }

    private void checkPhoneNotExists(Phone phone) {
        if (userRepository.existsByPhone(phone)) {
            throw BusinessExceptionEnum.PHONE_ALREADY_EXISTS.getException();
        }
    }
}