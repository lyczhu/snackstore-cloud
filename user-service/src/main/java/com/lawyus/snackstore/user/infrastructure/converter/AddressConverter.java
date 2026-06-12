package com.lawyus.snackstore.user.infrastructure.converter;

import com.lawyus.snackstore.user.domain.address.model.entity.Address;
import com.lawyus.snackstore.user.domain.address.model.valueobject.AddressDetail;
import com.lawyus.snackstore.user.domain.address.model.valueobject.ReceiverInfo;
import com.lawyus.snackstore.user.infrastructure.persistence.do_.AddressDO;

public class AddressConverter {

    public static AddressDO toDO(Address address) {
        AddressDO addressDO = new AddressDO();
        addressDO.setId(address.getId());
        addressDO.setUserId(address.getUserId());
        addressDO.setReceiverName(address.getReceiverInfo().getName());
        addressDO.setReceiverPhone(address.getReceiverInfo().getPhone().getValue());
        addressDO.setProvince(address.getAddressDetail().getProvince());
        addressDO.setCity(address.getAddressDetail().getCity());
        addressDO.setDistrict(address.getAddressDetail().getDistrict());
        addressDO.setDetail(address.getAddressDetail().getDetail());
        addressDO.setIsDefault(address.isDefault() ? 1 : 0);
        addressDO.setCreatedAt(address.getCreatedAt());
        addressDO.setUpdatedAt(address.getUpdatedAt());
        return addressDO;
    }

    public static Address toDomain(AddressDO addressDO) {
        ReceiverInfo receiverInfo = ReceiverInfo.of(
                addressDO.getReceiverName(),
                addressDO.getReceiverPhone()
        );
        AddressDetail addressDetail = AddressDetail.of(
                addressDO.getProvince(),
                addressDO.getCity(),
                addressDO.getDistrict(),
                addressDO.getDetail()
        );
        return Address.restore(
                addressDO.getId(),
                addressDO.getUserId(),
                receiverInfo,
                addressDetail,
                addressDO.getIsDefault() == 1,
                addressDO.getCreatedAt(),
                addressDO.getUpdatedAt()
        );
    }
}