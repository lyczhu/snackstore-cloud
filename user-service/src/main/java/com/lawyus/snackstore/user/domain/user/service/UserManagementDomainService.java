package com.lawyus.snackstore.user.domain.user.service;

import com.lawyus.snackstore.user.exception.BusinessExceptionEnum;
import com.lawyus.snackstore.user.domain.common.model.PageSpecification;
import com.lawyus.snackstore.user.domain.common.model.PagedResult;
import com.lawyus.snackstore.user.domain.user.model.entity.User;
import com.lawyus.snackstore.user.domain.user.model.valueobject.Password;
import com.lawyus.snackstore.user.domain.user.model.valueobject.Phone;
import com.lawyus.snackstore.user.domain.user.model.valueobject.UserRole;
import com.lawyus.snackstore.user.domain.user.model.valueobject.UserStatus;
import com.lawyus.snackstore.user.domain.user.port.PasswordEncoder;
import com.lawyus.snackstore.user.domain.user.repository.UserRepository;

import lombok.extern.slf4j.Slf4j;

@Slf4j
public class UserManagementDomainService {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;

    public UserManagementDomainService(UserRepository userRepository, PasswordEncoder passwordEncoder) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
    }

    /**
     * 管理员建号：内测期自助注册关闭后的唯一开号路径。
     * 不校验验证码（无真实短信通道），但手机号唯一性与口令加密与自助注册保持一致。
     *
     * <p>不发布 {@code UserRegisteredEvent}：该事件当前无任何监听方，且“注册”语义归
     * {@link UserAuthenticationDomainService}；若将来出现监听方（如欢迎短信），
     * 必须先决定管理员建号是否也走同一事件，再补发布。
     */
    public User createUser(Phone phone, String rawPassword, UserRole role, String nickname) {
        if (userRepository.existsByPhone(phone)) {
            throw BusinessExceptionEnum.PHONE_ALREADY_EXISTS.getException();
        }
        Password password = Password.fromRaw(rawPassword, passwordEncoder);
        User user = User.create(phone, password, role);
        if (nickname != null && !nickname.isBlank()) {
            user.updateProfile(nickname, null, null);
        }
        User saved = userRepository.save(user);
        log.info("管理员建号成功: userId={}, role={}", saved.getId(), role.getCode());
        return saved;
    }

    public User getUserById(Long id) {
        return userRepository.findById(id)
                .orElseThrow(BusinessExceptionEnum.USER_NOT_FOUND::getException);
    }

    public User updateUser(Long id, String nickname, String avatar, Phone phone) {
        User user = getUserById(id);
        user.updateProfile(nickname, avatar, phone);
        return userRepository.save(user);
    }

    public void changeUserStatus(Long id, UserStatus newStatus) {
        User user = getUserById(id);
        if (newStatus == UserStatus.DISABLED) {
            user.disable();
        } else {
            user.enable();
        }
        userRepository.save(user);
    }

    public PagedResult<User> getUserList(int pageNum, int pageSize) {
        return userRepository.findAll(PageSpecification.of(pageNum, pageSize));
    }

    public long count() {
        return userRepository.count();
    }
}