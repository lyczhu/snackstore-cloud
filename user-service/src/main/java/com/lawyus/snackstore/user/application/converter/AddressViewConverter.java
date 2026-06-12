package com.lawyus.snackstore.user.application.converter;

import com.lawyus.snackstore.user.domain.address.model.entity.Address;
import com.lawyus.snackstore.user.application.vo.AddressViewVO;

public class AddressViewConverter {
    
    public static AddressViewVO toViewVO(Address address) {
        return new AddressViewVO(
                address.getId(),
                address.getUserId(),
                address.getReceiverInfo().getName(),
                address.getReceiverInfo().getPhone().toString(),
                address.getAddressDetail().getProvince(),
                address.getAddressDetail().getCity(),
                address.getAddressDetail().getDistrict(),
                address.getAddressDetail().getDetail(),
                address.isDefault()
        );
    }
}
