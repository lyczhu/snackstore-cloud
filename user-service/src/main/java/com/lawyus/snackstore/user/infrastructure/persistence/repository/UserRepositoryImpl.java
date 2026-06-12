package com.lawyus.snackstore.user.infrastructure.persistence.repository;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.lawyus.snackstore.user.domain.common.event.DomainEventPublisher;
import com.lawyus.snackstore.user.domain.common.model.PageSpecification;
import com.lawyus.snackstore.user.domain.common.model.PagedResult;
import com.lawyus.snackstore.user.domain.user.model.entity.User;
import com.lawyus.snackstore.user.domain.user.model.valueobject.Phone;
import com.lawyus.snackstore.user.domain.user.repository.UserRepository;
import com.lawyus.snackstore.user.infrastructure.converter.UserConverter;
import com.lawyus.snackstore.user.infrastructure.persistence.do_.UserDO;
import com.lawyus.snackstore.user.infrastructure.persistence.mapper.UserMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;

@Repository
@Slf4j
public class UserRepositoryImpl implements UserRepository {

    private final UserMapper userMapper;
    private final DomainEventPublisher eventPublisher;

    public UserRepositoryImpl(UserMapper userMapper, DomainEventPublisher eventPublisher) {
        this.userMapper = userMapper;
        this.eventPublisher = eventPublisher;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public User save(User user) {
        UserDO userDO = UserConverter.toDO(user);
        if (userDO.getId() == null) {
            userMapper.insert(userDO);
            user.assignId(userDO.getId());
        } else {
            userMapper.updateById(userDO);
        }
        eventPublisher.publishAll(user.getDomainEvents());
        user.clearDomainEvents();
        return user;
    }

    @Override
    public Optional<User> findById(Long id) {
        UserDO userDO = userMapper.selectById(id);
        return Optional.ofNullable(userDO).map(UserConverter::toDomain);
    }

    @Override
    public Optional<User> findByPhone(Phone phone) {
        LambdaQueryWrapper<UserDO> wrapper = new LambdaQueryWrapper<UserDO>()
                .eq(UserDO::getPhone, phone.getValue());
        UserDO userDO = userMapper.selectOne(wrapper);
        return Optional.ofNullable(userDO).map(UserConverter::toDomain);
    }

    @Override
    public boolean existsByPhone(Phone phone) {
        LambdaQueryWrapper<UserDO> wrapper = new LambdaQueryWrapper<UserDO>()
                .eq(UserDO::getPhone, phone.getValue());
        return userMapper.selectCount(wrapper) > 0;
    }

    @Override
    public PagedResult<User> findAll(PageSpecification spec) {
        Page<UserDO> page = new Page<>(spec.pageNum(), spec.pageSize());
        LambdaQueryWrapper<UserDO> wrapper = new LambdaQueryWrapper<>();
        wrapper.orderByDesc(UserDO::getCreatedAt);
        Page<UserDO> result = userMapper.selectPage(page, wrapper);
        return new PagedResult<>(
                result.getRecords().stream().map(UserConverter::toDomain).toList(),
                result.getTotal(),
                spec.pageNum(),
                spec.pageSize()
        );
    }

    @Override
    public long count() {
        return userMapper.selectCount(null);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void deleteById(Long id) {
        userMapper.deleteById(id);
    }
}