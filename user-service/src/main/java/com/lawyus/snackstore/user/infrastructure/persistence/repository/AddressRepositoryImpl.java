package com.lawyus.snackstore.user.infrastructure.persistence.repository;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.lawyus.snackstore.user.domain.address.model.entity.Address;
import com.lawyus.snackstore.user.domain.address.repository.AddressRepository;
import com.lawyus.snackstore.user.domain.common.event.DomainEventPublisher;
import com.lawyus.snackstore.user.infrastructure.converter.AddressConverter;
import com.lawyus.snackstore.user.infrastructure.persistence.do_.AddressDO;
import com.lawyus.snackstore.user.infrastructure.persistence.mapper.AddressMapper;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;

@Repository
public class AddressRepositoryImpl implements AddressRepository {

    private final AddressMapper addressMapper;
    private final DomainEventPublisher eventPublisher;

    public AddressRepositoryImpl(AddressMapper addressMapper, DomainEventPublisher eventPublisher) {
        this.addressMapper = addressMapper;
        this.eventPublisher = eventPublisher;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Address save(Address address) {
        AddressDO addressDO = AddressConverter.toDO(address);
        if (addressDO.getId() == null) {
            addressMapper.insert(addressDO);
            address.assignId(addressDO.getId());
        } else {
            addressMapper.updateById(addressDO);
        }
        eventPublisher.publishAll(address.getDomainEvents());
        address.clearDomainEvents();
        return address;
    }

    @Override
    public Optional<Address> findById(Long id) {
        AddressDO addressDO = addressMapper.selectById(id);
        return Optional.ofNullable(addressDO).map(AddressConverter::toDomain);
    }

    @Override
    public List<Address> findByUserId(Long userId) {
        LambdaQueryWrapper<AddressDO> wrapper = new LambdaQueryWrapper<AddressDO>()
                .eq(AddressDO::getUserId, userId)
                .orderByDesc(AddressDO::getIsDefault)
                .orderByDesc(AddressDO::getUpdatedAt);
        List<AddressDO> addressDOs = addressMapper.selectList(wrapper);
        return addressDOs.stream().map(AddressConverter::toDomain).toList();
    }

    @Override
    public Optional<Address> findByIdAndUserId(Long id, Long userId) {
        LambdaQueryWrapper<AddressDO> wrapper = new LambdaQueryWrapper<AddressDO>()
                .eq(AddressDO::getId, id)
                .eq(AddressDO::getUserId, userId);
        AddressDO addressDO = addressMapper.selectOne(wrapper);
        return Optional.ofNullable(addressDO).map(AddressConverter::toDomain);
    }

    @Override
    public List<Address> findDefaultByUserId(Long userId) {
        LambdaQueryWrapper<AddressDO> wrapper = new LambdaQueryWrapper<AddressDO>()
                .eq(AddressDO::getUserId, userId)
                .eq(AddressDO::getIsDefault, 1);
        List<AddressDO> addressDOs = addressMapper.selectList(wrapper);
        return addressDOs.stream().map(AddressConverter::toDomain).toList();
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void deleteById(Long id) {
        addressMapper.deleteById(id);
    }

    @Override
    public boolean existsByIdAndUserId(Long id, Long userId) {
        LambdaQueryWrapper<AddressDO> wrapper = new LambdaQueryWrapper<AddressDO>()
                .eq(AddressDO::getId, id)
                .eq(AddressDO::getUserId, userId);
        return addressMapper.selectCount(wrapper) > 0;
    }
}