package com.lawyus.snackstore.user.infrastructure.converter;

import com.lawyus.snackstore.user.domain.user.model.entity.User;
import com.lawyus.snackstore.user.domain.user.model.valueobject.Password;
import com.lawyus.snackstore.user.domain.user.model.valueobject.Phone;
import com.lawyus.snackstore.user.domain.user.model.valueobject.UserRole;
import com.lawyus.snackstore.user.domain.user.model.valueobject.UserStatus;
import com.lawyus.snackstore.user.infrastructure.persistence.do_.UserDO;

public class UserConverter {

    public static UserDO toDO(User user) {
        UserDO userDO = new UserDO();
        userDO.setId(user.getId());
        userDO.setPhone(user.getPhone().getValue());
        userDO.setPassword(user.getPassword().getEncodedValue());
        userDO.setNickname(user.getNickname());
        userDO.setAvatar(user.getAvatar());
        userDO.setRole(user.getRole().getCode());
        userDO.setStatus(user.getStatus().getCode());
        userDO.setCreatedAt(user.getCreatedAt());
        userDO.setUpdatedAt(user.getUpdatedAt());
        return userDO;
    }

    public static User toDomain(UserDO userDO) {
        Phone phone = Phone.of(userDO.getPhone());
        Password password = Password.fromEncoded(userDO.getPassword());
        UserRole role = UserRole.fromCode(userDO.getRole());
        UserStatus status = UserStatus.fromCode(userDO.getStatus());
        return User.restore(
                userDO.getId(),
                phone,
                password,
                userDO.getNickname(),
                userDO.getAvatar(),
                role,
                status,
                userDO.getCreatedAt(),
                userDO.getUpdatedAt()
        );
    }
}