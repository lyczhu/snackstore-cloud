package com.lawyus.snackstore.user.domain.address.service;

import com.lawyus.snackstore.user.domain.address.model.entity.Address;
import com.lawyus.snackstore.user.domain.address.model.valueobject.AddressDetail;
import com.lawyus.snackstore.user.domain.address.model.valueobject.ReceiverInfo;
import com.lawyus.snackstore.user.domain.address.repository.AddressRepository;
import com.lawyus.snackstore.user.domain.common.event.DomainEventPublisher;
import com.lawyus.snackstore.user.exception.BusinessExceptionEnum;
import lombok.extern.slf4j.Slf4j;

import java.util.List;

@Slf4j
public class AddressManagementDomainService {

    private static final int MAX_ADDRESSES_PER_USER = 20;

    private final AddressRepository addressRepository;
    private final DomainEventPublisher eventPublisher;

    public AddressManagementDomainService(AddressRepository addressRepository,
                                          DomainEventPublisher eventPublisher) {
        this.addressRepository = addressRepository;
        this.eventPublisher = eventPublisher;
    }

    public Address createAddress(Long userId, ReceiverInfo receiverInfo, AddressDetail addressDetail,
                                  boolean isDefault) {
        if (addressRepository.countByUserId(userId) >= MAX_ADDRESSES_PER_USER) {
            throw new IllegalArgumentException("地址数量已达上限（" + MAX_ADDRESSES_PER_USER + "个）");
        }
        if (isDefault) {
            addressRepository.clearDefaultsByUserId(userId);
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
        boolean wasDefault = address.isDefault();
        if (isDefault != null && isDefault) {
            addressRepository.clearDefaultsByUserId(userId);
        }
        address.updateInfo(receiverInfo, addressDetail, isDefault);
        Address saved = addressRepository.save(address);

        // 原默认地址被置为非默认后，若用户已无默认地址，自动提升最新一条（与删除路径行为一致）
        if (Boolean.FALSE.equals(isDefault) && wasDefault
                && getUserAddresses(userId).stream().noneMatch(Address::isDefault)) {
            addressRepository.findLatestByUserId(userId)
                    .ifPresent(latest -> {
                        latest.setAsDefault();
                        addressRepository.save(latest);
                    });
        }
        return saved;
    }

    public void deleteAddress(Long addressId, Long userId) {
        Address address = findAddressBelongsToUser(addressId, userId);
        boolean wasDefault = address.isDefault();
        addressRepository.deleteById(addressId);
        if (wasDefault) {
            addressRepository.findLatestByUserId(userId)
                    .ifPresent(latest -> {
                        latest.setAsDefault();
                        addressRepository.save(latest);
                    });
        }
    }

    public Address setDefaultAddress(Long addressId, Long userId) {
        Address address = findAddressBelongsToUser(addressId, userId);
        addressRepository.clearDefaultsByUserId(userId);
        address.setAsDefault();
        return addressRepository.save(address);
    }

    public List<Address> getUserAddresses(Long userId) {
        return addressRepository.findByUserId(userId);
    }

    public Address getAddressByIdAndUserId(Long addressId, Long userId) {
        return findAddressBelongsToUser(addressId, userId);
    }

    private Address findAddressBelongsToUser(Long addressId, Long userId) {
        return addressRepository.findByIdAndUserId(addressId, userId)
                .orElseThrow(() -> BusinessExceptionEnum.DATA_NOT_FOUND.getException("地址不存在"));
    }
}