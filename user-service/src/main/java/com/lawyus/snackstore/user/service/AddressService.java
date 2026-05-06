package com.lawyus.snackstore.user.service;

import com.lawyus.snackstore.user.model.dto.AddressDTO;
import com.lawyus.snackstore.user.model.vo.AddressVO;

import java.util.List;

public interface AddressService {

    List<AddressVO> getAddressList(Long userId);

    AddressVO getAddressById(Long id, Long userId);

    AddressVO createAddress(Long userId, AddressDTO dto);

    AddressVO updateAddress(Long id, Long userId, AddressDTO dto);

    void deleteAddress(Long id, Long userId);

    void setDefaultAddress(Long id, Long userId);
}
