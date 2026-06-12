package com.lawyus.snackstore.user.domain.address.service;

import com.lawyus.snackstore.user.domain.address.model.entity.Address;
import com.lawyus.snackstore.user.domain.address.model.valueobject.AddressDetail;
import com.lawyus.snackstore.user.domain.address.model.valueobject.ReceiverInfo;
import com.lawyus.snackstore.user.domain.address.repository.AddressRepository;
import com.lawyus.snackstore.user.domain.common.event.DomainEventPublisher;
import com.lawyus.snackstore.common.exception.BusinessExceptionEnum;
import lombok.extern.slf4j.Slf4j;

import java.util.List;

@Slf4j
public class AddressManagementDomainService {

    private final AddressRepository addressRepository;
    private final DomainEventPublisher eventPublisher;

    public AddressManagementDomainService(AddressRepository addressRepository,
                                          DomainEventPublisher eventPublisher) {
        this.addressRepository = addressRepository;
        this.eventPublisher = eventPublisher;
    }

    public Address createAddress(Long userId, ReceiverInfo receiverInfo, AddressDetail addressDetail,
                                  boolean isDefault) {
        if (isDefault) {
            clearExistingDefaults(userId);
        }

        Address address = Address.create(userId, receiverInfo, addressDetail, isDefault);
        Address saved = addressRepository.save(address);
        saved.onCreated();
        eventPublisher.publishAll(saved.getDomainEvents());
        saved.clearDomainEvents();
        return saved;
    }

    public Address updateAddress(Long addressId, Long userId, ReceiverInfo receiverInfo,
                                  AddressDetail addressDetail, Boolean isDefault) {
        Address address = findAddressBelongsToUser(addressId, userId);
        if (isDefault != null && isDefault) {
            clearExistingDefaults(userId);
        }
        address.updateInfo(receiverInfo, addressDetail, isDefault);
        return addressRepository.save(address);
    }

    public void deleteAddress(Long addressId, Long userId) {
        if (!addressRepository.existsByIdAndUserId(addressId, userId)) {
            throw BusinessExceptionEnum.DATA_NOT_FOUND.getException("地址不存在");
        }
        addressRepository.deleteById(addressId);
    }

    public Address setDefaultAddress(Long addressId, Long userId) {
        Address address = findAddressBelongsToUser(addressId, userId);
        clearExistingDefaults(userId);
        address.setAsDefault();
        return addressRepository.save(address);
    }

    public List<Address> getUserAddresses(Long userId) {
        return addressRepository.findByUserId(userId);
    }

    public Address getAddressByIdAndUserId(Long addressId, Long userId) {
        return findAddressBelongsToUser(addressId, userId);
    }

    private void clearExistingDefaults(Long userId) {
        List<Address> defaults = addressRepository.findDefaultByUserId(userId);
        for (Address addr : defaults) {
            addr.unsetDefault();
            addressRepository.save(addr);
        }
    }

    private Address findAddressBelongsToUser(Long addressId, Long userId) {
        return addressRepository.findByIdAndUserId(addressId, userId)
                .orElseThrow(() -> BusinessExceptionEnum.DATA_NOT_FOUND.getException("地址不存在"));
    }
}