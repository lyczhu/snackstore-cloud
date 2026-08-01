package com.lawyus.snackstore.user.presentation.controller;

import com.lawyus.snackstore.common.response.Result;
import com.lawyus.snackstore.user.application.dto.AddressCreateCommand;
import com.lawyus.snackstore.user.application.dto.AddressUpdateCommand;
import com.lawyus.snackstore.user.application.service.AddressApplicationService;
import com.lawyus.snackstore.user.application.vo.AddressViewVO;
import com.lawyus.snackstore.user.exception.BusinessExceptionEnum;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/users/{userId}/addresses")
public class AddressController {

    private final AddressApplicationService addressApplicationService;

    public AddressController(AddressApplicationService addressApplicationService) {
        this.addressApplicationService = addressApplicationService;
    }

    @GetMapping
    public Result<List<AddressViewVO>> getAddressList(@PathVariable("userId") Long userId,
                                                      @RequestHeader("X-User-Id") Long authUserId) {
        assertOwner(userId, authUserId);
        return Result.success(addressApplicationService.getAddressList(userId));
    }

    @GetMapping("/{id}")
    public Result<AddressViewVO> getAddressById(@PathVariable("userId") Long userId,
                                                 @PathVariable Long id,
                                                 @RequestHeader("X-User-Id") Long authUserId) {
        assertOwner(userId, authUserId);
        return Result.success(addressApplicationService.getAddressById(id, userId));
    }

    @PostMapping
    public Result<AddressViewVO> createAddress(@PathVariable("userId") Long userId,
                                                @RequestHeader("X-User-Id") Long authUserId,
                                                @Valid @RequestBody AddressCreateCommand command) {
        assertOwner(userId, authUserId);
        return Result.success(addressApplicationService.createAddress(userId, command));
    }

    @PutMapping("/{id}")
    public Result<AddressViewVO> updateAddress(@PathVariable("userId") Long userId,
                                                @PathVariable Long id,
                                                @RequestHeader("X-User-Id") Long authUserId,
                                                @Valid @RequestBody AddressUpdateCommand command) {
        assertOwner(userId, authUserId);
        return Result.success(addressApplicationService.updateAddress(id, userId, command));
    }

    @DeleteMapping("/{id}")
    public Result<Void> deleteAddress(@PathVariable("userId") Long userId,
                                      @PathVariable Long id,
                                      @RequestHeader("X-User-Id") Long authUserId) {
        assertOwner(userId, authUserId);
        addressApplicationService.deleteAddress(id, userId);
        return Result.success(null);
    }

    @PatchMapping("/{id}/default")
    public Result<Void> setDefaultAddress(@PathVariable("userId") Long userId,
                                          @PathVariable Long id,
                                          @RequestHeader("X-User-Id") Long authUserId) {
        assertOwner(userId, authUserId);
        addressApplicationService.setDefaultAddress(id, userId);
        return Result.success(null);
    }

    private void assertOwner(Long userId, Long authUserId) {
        if (!authUserId.equals(userId)) {
            throw BusinessExceptionEnum.ACCESS_FORBIDDEN.getException("无权访问其他用户的地址");
        }
    }
}
