package com.lawyus.snackstore.user.application.service;

import com.lawyus.snackstore.user.application.dto.AddressCreateCommand;
import com.lawyus.snackstore.user.application.dto.AddressUpdateCommand;
import com.lawyus.snackstore.user.application.vo.AddressViewVO;

import java.util.List;

public interface AddressApplicationService {

    List<AddressViewVO> getAddressList(Long userId);

    AddressViewVO getAddressById(Long id, Long userId);

    AddressViewVO createAddress(Long userId, AddressCreateCommand command);

    AddressViewVO updateAddress(Long id, Long userId, AddressUpdateCommand command);

    void deleteAddress(Long id, Long userId);

    void setDefaultAddress(Long id, Long userId);
}