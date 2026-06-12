package com.lawyus.snackstore.user.domain.user.service;

import com.lawyus.snackstore.common.exception.BusinessExceptionEnum;
import com.lawyus.snackstore.user.domain.common.model.PageSpecification;
import com.lawyus.snackstore.user.domain.common.model.PagedResult;
import com.lawyus.snackstore.user.domain.user.model.entity.User;
import com.lawyus.snackstore.user.domain.user.model.valueobject.Phone;
import com.lawyus.snackstore.user.domain.user.model.valueobject.UserStatus;
import com.lawyus.snackstore.user.domain.user.repository.UserRepository;

import lombok.extern.slf4j.Slf4j;

@Slf4j
public class UserManagementDomainService {

    private final UserRepository userRepository;

    public UserManagementDomainService(UserRepository userRepository) {
        this.userRepository = userRepository;
    }

    public User getUserById(Long id) {
        return userRepository.findById(id)
                .orElseThrow(() -> BusinessExceptionEnum.USER_NOT_FOUND.getException());
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
}