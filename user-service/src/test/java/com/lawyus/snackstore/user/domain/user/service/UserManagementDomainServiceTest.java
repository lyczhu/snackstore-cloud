package com.lawyus.snackstore.user.domain.user.service;

import com.lawyus.snackstore.common.response.ResultCode;
import com.lawyus.snackstore.user.domain.user.model.entity.User;
import com.lawyus.snackstore.user.domain.user.model.valueobject.Phone;
import com.lawyus.snackstore.user.domain.user.model.valueobject.UserRole;
import com.lawyus.snackstore.user.domain.user.port.PasswordEncoder;
import com.lawyus.snackstore.user.domain.user.repository.UserRepository;
import com.lawyus.snackstore.user.exception.BusinessException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowableOfType;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 管理员建号（内测期自助注册关闭后的唯一开号路径）的特征化测试。
 *
 * <p>这里钉住三条不变量：手机号唯一性必须先查后写、口令必须经编码后落库、
 * 角色缺省与昵称缺省不随实现漂移。三者坏掉都不会编译报错，只会静默产生
 * 「明文口令」「重号账号」这类不可逆数据问题。
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("UserManagementDomainService 管理员建号")
class UserManagementDomainServiceTest {

    private static final String PHONE_VALUE = "13800000000";

    @Mock
    private UserRepository userRepository;

    @Mock
    private PasswordEncoder passwordEncoder;

    private UserManagementDomainService service;

    @BeforeEach
    void setUp() {
        service = new UserManagementDomainService(userRepository, passwordEncoder);
    }

    private static Phone phone() {
        return Phone.of(PHONE_VALUE);
    }

    @Test
    @DisplayName("建号落库：口令经编码、角色按指令写入、显式昵称覆盖默认值")
    void createsUserWithEncodedPasswordAndGivenRole() {
        when(userRepository.existsByPhone(any(Phone.class))).thenReturn(false);
        when(passwordEncoder.encode("secret123")).thenReturn("encoded-secret");
        when(userRepository.save(any(User.class))).thenAnswer(invocation -> {
            User user = invocation.getArgument(0);
            user.assignId(7L);
            return user;
        });

        User created = service.createUser(phone(), "secret123", UserRole.ADMIN, "运营小王");

        ArgumentCaptor<User> captor = ArgumentCaptor.forClass(User.class);
        verify(userRepository).save(captor.capture());
        User saved = captor.getValue();

        assertThat(saved.getPassword().getEncodedValue())
                .as("口令必须以编码值落库，绝不能是明文")
                .isEqualTo("encoded-secret");
        assertThat(saved.getRole()).isEqualTo(UserRole.ADMIN);
        assertThat(saved.getNickname()).isEqualTo("运营小王");
        assertThat(saved.isActive()).isTrue();
        assertThat(created.getId()).isEqualTo(7L);
    }

    @Test
    @DisplayName("手机号已存在：拒绝建号且绝不落库")
    void rejectsDuplicatePhoneBeforeSaving() {
        when(userRepository.existsByPhone(any(Phone.class))).thenReturn(true);

        BusinessException error = catchThrowableOfType(
                () -> service.createUser(phone(), "secret123", UserRole.USER, null),
                BusinessException.class);

        assertThat(error).isNotNull();
        assertThat(error.getCode()).isEqualTo(ResultCode.PHONE_ALREADY_EXISTS.getCode());
        verify(userRepository, never()).save(any(User.class));
    }

    @Test
    @DisplayName("不传昵称：沿用 User.create 的默认昵称（手机号后四位）")
    void fallsBackToDefaultNickname() {
        when(userRepository.existsByPhone(any(Phone.class))).thenReturn(false);
        when(passwordEncoder.encode("secret123")).thenReturn("encoded-secret");
        when(userRepository.save(any(User.class))).thenAnswer(invocation -> invocation.getArgument(0));

        User created = service.createUser(phone(), "secret123", UserRole.USER, "  ");

        assertThat(created.getNickname()).isEqualTo("用户0000");
    }
}
