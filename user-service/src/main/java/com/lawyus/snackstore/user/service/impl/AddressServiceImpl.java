package com.lawyus.snackstore.user.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.lawyus.snackstore.common.exception.BusinessException;
import com.lawyus.snackstore.common.exception.BusinessExceptionEnum;
import com.lawyus.snackstore.user.mapper.AddressMapper;
import com.lawyus.snackstore.user.model.dto.AddressDTO;
import com.lawyus.snackstore.user.model.entity.Address;
import com.lawyus.snackstore.user.model.vo.AddressVO;
import com.lawyus.snackstore.user.service.AddressService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
public class AddressServiceImpl implements AddressService {

    private final AddressMapper addressMapper;

    public AddressServiceImpl(AddressMapper addressMapper) {
        this.addressMapper = addressMapper;
    }

    @Override
    public List<AddressVO> getAddressList(Long userId) {
        List<Address> addresses = addressMapper.selectList(
                new LambdaQueryWrapper<Address>()
                        .eq(Address::getUserId, userId)
                        .orderByDesc(Address::getIsDefault)
                        .orderByDesc(Address::getUpdatedAt));
        return addresses.stream().map(this::convertToVO).toList();
    }

    @Override
    public AddressVO getAddressById(Long id, Long userId) {
        Address address = addressMapper.selectOne(
                new LambdaQueryWrapper<Address>()
                        .eq(Address::getId, id)
                        .eq(Address::getUserId, userId));
        if (address == null) {
            throw BusinessExceptionEnum.DATA_NOT_FOUND.getException("地址不存在");
        }
        return convertToVO(address);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public AddressVO createAddress(Long userId, AddressDTO dto) {
        if (dto.getIsDefault() != null && dto.getIsDefault() == 1) {
            clearDefaultAddress(userId);
        }
        Address address = new Address();
        address.setUserId(userId);
        address.setReceiverName(dto.getReceiverName());
        address.setReceiverPhone(dto.getReceiverPhone());
        address.setProvince(dto.getProvince());
        address.setCity(dto.getCity());
        address.setDistrict(dto.getDistrict());
        address.setDetail(dto.getDetail());
        address.setIsDefault(dto.getIsDefault() != null ? dto.getIsDefault() : 0);
        addressMapper.insert(address);
        return convertToVO(address);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public AddressVO updateAddress(Long id, Long userId, AddressDTO dto) {
        Address address = addressMapper.selectOne(
                new LambdaQueryWrapper<Address>()
                        .eq(Address::getId, id)
                        .eq(Address::getUserId, userId));
        if (address == null) {
            throw BusinessExceptionEnum.DATA_NOT_FOUND.getException("地址不存在");
        }
        if (dto.getIsDefault() != null && dto.getIsDefault() == 1) {
            clearDefaultAddress(userId);
        }
        address.setReceiverName(dto.getReceiverName());
        address.setReceiverPhone(dto.getReceiverPhone());
        address.setProvince(dto.getProvince());
        address.setCity(dto.getCity());
        address.setDistrict(dto.getDistrict());
        address.setDetail(dto.getDetail());
        if (dto.getIsDefault() != null) {
            address.setIsDefault(dto.getIsDefault());
        }
        addressMapper.updateById(address);
        return convertToVO(address);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void deleteAddress(Long id, Long userId) {
        int rows = addressMapper.delete(
                new LambdaQueryWrapper<Address>()
                        .eq(Address::getId, id)
                        .eq(Address::getUserId, userId));
        if (rows == 0) {
            throw BusinessExceptionEnum.DATA_NOT_FOUND.getException("地址不存在");
        }
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void setDefaultAddress(Long id, Long userId) {
        Address address = addressMapper.selectOne(
                new LambdaQueryWrapper<Address>()
                        .eq(Address::getId, id)
                        .eq(Address::getUserId, userId));
        if (address == null) {
            throw BusinessExceptionEnum.DATA_NOT_FOUND.getException("地址不存在");
        }
        clearDefaultAddress(userId);
        address.setIsDefault(1);
        addressMapper.updateById(address);
    }

    private void clearDefaultAddress(Long userId) {
        addressMapper.update(null,
                new LambdaUpdateWrapper<Address>()
                        .eq(Address::getUserId, userId)
                        .eq(Address::getIsDefault, 1)
                        .set(Address::getIsDefault, 0));
    }

    private AddressVO convertToVO(Address address) {
        AddressVO vo = new AddressVO();
        vo.setId(address.getId());
        vo.setUserId(address.getUserId());
        vo.setReceiverName(address.getReceiverName());
        vo.setReceiverPhone(address.getReceiverPhone());
        vo.setProvince(address.getProvince());
        vo.setCity(address.getCity());
        vo.setDistrict(address.getDistrict());
        vo.setDetail(address.getDetail());
        vo.setIsDefault(address.getIsDefault());
        return vo;
    }
}
