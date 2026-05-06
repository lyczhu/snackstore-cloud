package com.lawyus.snackstore.user.controller;

import com.lawyus.snackstore.common.response.Result;
import com.lawyus.snackstore.user.model.dto.AddressDTO;
import com.lawyus.snackstore.user.model.vo.AddressVO;
import com.lawyus.snackstore.user.service.AddressService;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/address")
public class AddressController {

    private final AddressService addressService;

    public AddressController(AddressService addressService) {
        this.addressService = addressService;
    }

    @GetMapping("/list")
    public Result<List<AddressVO>> getAddressList(@RequestHeader("X-User-Id") Long userId) {
        return Result.success(addressService.getAddressList(userId));
    }

    @GetMapping("/{id}")
    public Result<AddressVO> getAddressById(@PathVariable Long id, @RequestHeader("X-User-Id") Long userId) {
        return Result.success(addressService.getAddressById(id, userId));
    }

    @PostMapping
    public Result<AddressVO> createAddress(@RequestHeader("X-User-Id") Long userId,
                                           @Valid @RequestBody AddressDTO dto) {
        return Result.success(addressService.createAddress(userId, dto));
    }

    @PutMapping("/{id}")
    public Result<AddressVO> updateAddress(@PathVariable Long id,
                                           @RequestHeader("X-User-Id") Long userId,
                                           @Valid @RequestBody AddressDTO dto) {
        return Result.success(addressService.updateAddress(id, userId, dto));
    }

    @DeleteMapping("/{id}")
    public Result<Void> deleteAddress(@PathVariable Long id, @RequestHeader("X-User-Id") Long userId) {
        addressService.deleteAddress(id, userId);
        return Result.success(null);
    }

    @PutMapping("/{id}/default")
    public Result<Void> setDefaultAddress(@PathVariable Long id, @RequestHeader("X-User-Id") Long userId) {
        addressService.setDefaultAddress(id, userId);
        return Result.success(null);
    }
}
