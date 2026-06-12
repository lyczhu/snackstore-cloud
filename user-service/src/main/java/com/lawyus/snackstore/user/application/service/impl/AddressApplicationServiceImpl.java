package com.lawyus.snackstore.user.application.service.impl;

import com.lawyus.snackstore.user.application.dto.AddressCreateCommand;
import com.lawyus.snackstore.user.application.dto.AddressUpdateCommand;
import com.lawyus.snackstore.user.application.service.AddressApplicationService;
import com.lawyus.snackstore.user.application.vo.AddressViewVO;
import com.lawyus.snackstore.user.application.converter.AddressViewConverter;
import com.lawyus.snackstore.user.domain.address.model.entity.Address;
import com.lawyus.snackstore.user.domain.address.model.valueobject.AddressDetail;
import com.lawyus.snackstore.user.domain.address.model.valueobject.ReceiverInfo;
import com.lawyus.snackstore.user.domain.address.service.AddressManagementDomainService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@Slf4j
public class AddressApplicationServiceImpl implements AddressApplicationService {

    private final AddressManagementDomainService addressManagementDomainService;

    public AddressApplicationServiceImpl(AddressManagementDomainService addressManagementDomainService) {
        this.addressManagementDomainService = addressManagementDomainService;
    }

    @Override
    public List<AddressViewVO> getAddressList(Long userId) {
        List<Address> addresses = addressManagementDomainService.getUserAddresses(userId);
        return addresses.stream()
                .map(AddressViewConverter::toViewVO)
                .toList();
    }

    @Override
    public AddressViewVO getAddressById(Long id, Long userId) {
        Address address = addressManagementDomainService.getAddressByIdAndUserId(id, userId);
        return AddressViewConverter.toViewVO(address);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public AddressViewVO createAddress(Long userId, AddressCreateCommand command) {
        ReceiverInfo receiverInfo = ReceiverInfo.of(
                command.getReceiverName(),
                command.getReceiverPhone()
        );
        AddressDetail addressDetail = AddressDetail.of(
                command.getProvince(),
                command.getCity(),
                command.getDistrict(),
                command.getDetail()
        );
        boolean isDefault = command.getIsDefault() != null && command.getIsDefault();

        Address address = addressManagementDomainService.createAddress(userId, receiverInfo, addressDetail, isDefault);
        return AddressViewConverter.toViewVO(address);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public AddressViewVO updateAddress(Long id, Long userId, AddressUpdateCommand command) {
        ReceiverInfo receiverInfo = ReceiverInfo.of(
                command.getReceiverName(),
                command.getReceiverPhone()
        );
        AddressDetail addressDetail = AddressDetail.of(
                command.getProvince(),
                command.getCity(),
                command.getDistrict(),
                command.getDetail()
        );

        Address address = addressManagementDomainService.updateAddress(id, userId, receiverInfo,
                addressDetail, command.getIsDefault());
        return AddressViewConverter.toViewVO(address);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void deleteAddress(Long id, Long userId) {
        addressManagementDomainService.deleteAddress(id, userId);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void setDefaultAddress(Long id, Long userId) {
        addressManagementDomainService.setDefaultAddress(id, userId);
    }
}