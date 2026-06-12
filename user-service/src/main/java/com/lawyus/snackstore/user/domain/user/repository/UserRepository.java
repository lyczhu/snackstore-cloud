package com.lawyus.snackstore.user.domain.user.repository;

import com.lawyus.snackstore.user.domain.common.model.PageSpecification;
import com.lawyus.snackstore.user.domain.common.model.PagedResult;
import com.lawyus.snackstore.user.domain.user.model.entity.User;
import com.lawyus.snackstore.user.domain.user.model.valueobject.Phone;

import java.util.Optional;

public interface UserRepository {

    User save(User user);

    Optional<User> findById(Long id);

    Optional<User> findByPhone(Phone phone);

    boolean existsByPhone(Phone phone);

    PagedResult<User> findAll(PageSpecification spec);

    long count();

    void deleteById(Long id);
}