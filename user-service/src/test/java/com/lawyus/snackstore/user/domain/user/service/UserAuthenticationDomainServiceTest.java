package com.lawyus.snackstore.user.domain.user.service;

import com.lawyus.snackstore.user.domain.common.event.DomainEventPublisher;
import com.lawyus.snackstore.user.domain.user.model.entity.User;
import com.lawyus.snackstore.user.domain.user.model.valueobject.Password;
import com.lawyus.snackstore.user.domain.user.model.valueobject.Phone;
import com.lawyus.snackstore.user.domain.user.model.valueobject.UserRole;
import com.lawyus.snackstore.user.domain.user.event.UserRegisteredEvent;
import com.lawyus.snackstore.user.domain.user.port.LoginLockPort;
import com.lawyus.snackstore.user.domain.user.port.PasswordEncoder;
import com.lawyus.snackstore.user.domain.user.port.VerificationCodePort;
import com.lawyus.snackstore.user.domain.user.repository.UserRepository;
import com.lawyus.snackstore.user.exception.BusinessException;
import com.lawyus.snackstore.user.exception.BusinessExceptionEnum;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * 注册与登录领域服务的特征化测试。
 *
 * <p>这里承载的是账号安全的检查顺序：锁定检查必须早于任何查库与口令比较，禁用检查必须早于口令比较，
 * 验证码失败必须计数（否则爆破没有成本），管理员登录失败不得暴露账号是否存在。
 * 顺序一旦被打乱，编译与启动都正常，只有跑业务才发现——正是必须被测试钉住的那类缺陷。
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("UserAuthenticationDomainService 注册与登录安全检查顺序")
class UserAuthenticationDomainServiceTest {

    private static final String PHONE_VALUE = "13800000000";

    @Mock
    private UserRepository userRepository;

    @Mock
    private PasswordEncoder passwordEncoder;

    @Mock
    private DomainEventPublisher eventPublisher;

    @Mock
    private VerificationCodePort verificationCodePort;

    @Mock
    private LoginLockPort loginLockPort;

    private UserAuthenticationDomainService service;

    @BeforeEach
    void setUp() {
        service = new UserAuthenticationDomainService(userRepository, passwordEncoder,
                eventPublisher, verificationCodePort, loginLockPort);
    }

    private static Phone phone() {
        return Phone.of(PHONE_VALUE);
    }

    private static User existingUser(UserRole role, boolean enabled) {
        User user = User.create(phone(), Password.fromEncoded("hashed-password"), role);
        user.assignId(1L);
        if (!enabled) {
            user.disable();
        }
        return user;
    }

    @Test
    @DisplayName("注册：验证码失败锁定位优先拦截，不查库也不建用户")
    void registerIsBlockedWhileVerificationLocked() {
        when(verificationCodePort.isVerificationLocked(PHONE_VALUE)).thenReturn(true);

        assertThatThrownBy(() -> service.register(phone(), "Secret123", "123456", "123456"))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("验证码错误次数过多");

        verifyNoInteractions(userRepository, passwordEncoder);
    }

    @Test
    @DisplayName("注册：验证码不存在视为过期，且不推进到查重步骤")
    void registerTreatsMissingCodeAsExpired() {
        assertThatThrownBy(() -> service.register(phone(), "Secret123", "123456", null))
                .isInstanceOf(BusinessException.class)
                .hasMessage(BusinessExceptionEnum.SMS_CODE_EXPIRED.getMessage());

        verify(userRepository, never()).existsByPhone(any());
    }

    @Test
    @DisplayName("注册：验证码不匹配必须累计失败次数（否则爆破没有成本）")
    void registerCountsVerificationFailures() {
        assertThatThrownBy(() -> service.register(phone(), "Secret123", "000000", "123456"))
                .isInstanceOf(BusinessException.class)
                .hasMessage(BusinessExceptionEnum.SMS_CODE_ERROR.getMessage());

        verify(verificationCodePort).incrementVerificationFail(PHONE_VALUE);
        verifyNoInteractions(userRepository);
    }

    @Test
    @DisplayName("注册：手机号已存在则拒绝，不创建用户也不发事件")
    void registerRejectsExistingPhone() {
        when(userRepository.existsByPhone(phone())).thenReturn(true);

        assertThatThrownBy(() -> service.register(phone(), "Secret123", "123456", "123456"))
                .isInstanceOf(BusinessException.class)
                .hasMessage(BusinessExceptionEnum.PHONE_ALREADY_EXISTS.getMessage());

        verify(userRepository, never()).save(any());
        verifyNoInteractions(eventPublisher);
    }

    @Test
    @DisplayName("注册成功：以 USER 角色落库并发放注册事件")
    void registerPersistsAsUserAndPublishesEvent() {
        when(passwordEncoder.encode("Secret123")).thenReturn("hashed-password");
        when(userRepository.save(any(User.class))).thenAnswer(inv -> {
            User u = inv.getArgument(0);
            u.assignId(9L);
            return u;
        });
        // AggregateRoot.getDomainEvents() 返回内部列表的 unmodifiableList 活视图，
        // 随后 clearDomainEvents() 会清空同一列表；因此必须在调用时刻快照，
        // 用 ArgumentCaptor 拿到的引用会变成空集合。
        List<Object> published = new java.util.ArrayList<>();
        org.mockito.Mockito.doAnswer(inv -> {
            ((List<?>) inv.getArgument(0)).forEach(published::add);
            return null;
        }).when(eventPublisher).publishAll(anyList());

        User saved = service.register(phone(), "Secret123", "123456", "123456");

        ArgumentCaptor<User> captor = ArgumentCaptor.forClass(User.class);
        verify(userRepository).save(captor.capture());
        assertThat(captor.getValue().isAdmin()).isFalse();

        assertThat(published).hasSize(1);
        assertThat(published.getFirst()).isInstanceOf(UserRegisteredEvent.class);
        assertThat(saved.getDomainEvents()).isEmpty();
    }

    @Test
    @DisplayName("登录：处于锁定窗口时不查库、不比口令")
    void loginIsBlockedWhileLocked() {
        when(loginLockPort.isLocked(PHONE_VALUE)).thenReturn(true);

        assertThatThrownBy(() -> service.login(phone(), "Secret123"))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("密码错误次数过多");

        verifyNoInteractions(userRepository, passwordEncoder);
    }

    @Test
    @DisplayName("登录：手机号未注册返回 USER_NOT_FOUND")
    void loginReportsUnknownPhone() {
        when(userRepository.findByPhone(phone())).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.login(phone(), "Secret123"))
                .isInstanceOf(BusinessException.class)
                .hasMessage(BusinessExceptionEnum.USER_NOT_FOUND.getMessage());
    }

    @Test
    @DisplayName("登录：禁用账号先于口令比较被拒绝（不泄漏口令是否正确）")
    void disabledUserIsRejectedBeforePasswordCheck() {
        when(userRepository.findByPhone(phone()))
                .thenReturn(Optional.of(existingUser(UserRole.USER, false)));

        assertThatThrownBy(() -> service.login(phone(), "Secret123"))
                .isInstanceOf(BusinessException.class)
                .hasMessage(BusinessExceptionEnum.USER_DISABLED.getMessage());

        verifyNoInteractions(passwordEncoder);
        verify(loginLockPort, never()).incrementFail(anyString());
    }

    @Test
    @DisplayName("登录：口令错误累计失败次数")
    void wrongPasswordCountsFail() {
        when(userRepository.findByPhone(phone()))
                .thenReturn(Optional.of(existingUser(UserRole.USER, true)));
        when(passwordEncoder.matches("wrong-password", "hashed-password")).thenReturn(false);
        when(loginLockPort.incrementFail(PHONE_VALUE)).thenReturn(3L);

        assertThatThrownBy(() -> service.login(phone(), "wrong-password"))
                .isInstanceOf(BusinessException.class)
                .hasMessage(BusinessExceptionEnum.PASSWORD_ERROR.getMessage());

        verify(loginLockPort).incrementFail(PHONE_VALUE);
        verify(loginLockPort, never()).resetFail(anyString());
    }

    @Test
    @DisplayName("登录成功：清零失败计数并发放登录事件")
    void successfulLoginResetsLockAndPublishesEvent() {
        User user = existingUser(UserRole.USER, true);
        when(userRepository.findByPhone(phone())).thenReturn(Optional.of(user));
        when(passwordEncoder.matches("Secret123", "hashed-password")).thenReturn(true);

        User result = service.login(phone(), "Secret123");

        verify(loginLockPort).resetFail(PHONE_VALUE);
        assertThat(result.getDomainEvents()).isEmpty();
        verify(eventPublisher).publishAll(anyList());
    }

    @Test
    @DisplayName("管理员登录：非管理员账号返回与「账号不存在」相同的错误，避免用户枚举")
    void adminLoginDoesNotRevealAccountExistence() {
        when(userRepository.findByPhone(phone()))
                .thenReturn(Optional.of(existingUser(UserRole.USER, true)));
        when(passwordEncoder.matches("Secret123", "hashed-password")).thenReturn(true);

        assertThatThrownBy(() -> service.adminLogin(phone(), "Secret123"))
                .isInstanceOf(BusinessException.class)
                .hasMessage("用户名或密码错误")
                .extracting("code")
                .isEqualTo(BusinessExceptionEnum.USER_NOT_FOUND.getCode());
    }

    @Test
    @DisplayName("管理员登录：管理员账号正常放行")
    void adminLoginSucceedsForAdmin() {
        when(userRepository.findByPhone(phone()))
                .thenReturn(Optional.of(existingUser(UserRole.ADMIN, true)));
        when(passwordEncoder.matches("Secret123", "hashed-password")).thenReturn(true);

        assertThat(service.adminLogin(phone(), "Secret123").isAdmin()).isTrue();
    }
}
