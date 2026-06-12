package com.lawyus.snackstore.user.application.converter;

import com.lawyus.snackstore.user.domain.user.model.entity.User;
import com.lawyus.snackstore.user.domain.user.model.valueobject.Phone;
import com.lawyus.snackstore.user.application.vo.UserViewVO;

public class UserViewConverter {
    
    public static UserViewVO toViewVO(User user) {
        return new UserViewVO(
                user.getId(),
                user.getPhone().getValue(),
                user.getNickname(),
                user.getAvatar(),
                user.getRole().getCode(),
                user.getStatus().getCode()
        );
    }
}
