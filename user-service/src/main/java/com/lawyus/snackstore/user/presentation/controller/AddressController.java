package com.lawyus.snackstore.user.presentation.controller;

import com.lawyus.snackstore.common.response.Result;
import com.lawyus.snackstore.user.application.dto.AddressCreateCommand;
import com.lawyus.snackstore.user.application.dto.AddressUpdateCommand;
import com.lawyus.snackstore.user.application.service.AddressApplicationService;
import com.lawyus.snackstore.user.application.vo.AddressViewVO;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/user/address")
public class AddressController {
    
    private final AddressApplicationService addressApplicationService;
    
    public AddressController(AddressApplicationService addressApplicationService) {
        this.addressApplicationService = addressApplicationService;
    }
    
    @GetMapping("/list")
    public Result<List<AddressViewVO>> getAddressList(@RequestHeader("X-User-Id") Long userId) {
        return Result.success(addressApplicationService.getAddressList(userId));
    }
    
    @GetMapping("/{id}")
    public Result<AddressViewVO> getAddressById(@PathVariable Long id, @RequestHeader("X-User-Id") Long userId) {
        return Result.success(addressApplicationService.getAddressById(id, userId));
    }
    
    @PostMapping
    public Result<AddressViewVO> createAddress(@RequestHeader("X-User-Id") Long userId,
                                               @Valid @RequestBody AddressCreateCommand command) {
        return Result.success(addressApplicationService.createAddress(userId, command));
    }
    
    @PutMapping("/{id}")
    public Result<AddressViewVO> updateAddress(@PathVariable Long id,
                                               @RequestHeader("X-User-Id") Long userId,
                                               @Valid @RequestBody AddressUpdateCommand command) {
        return Result.success(addressApplicationService.updateAddress(id, userId, command));
    }
    
    @DeleteMapping("/{id}")
    public Result<Void> deleteAddress(@PathVariable Long id, @RequestHeader("X-User-Id") Long userId) {
        addressApplicationService.deleteAddress(id, userId);
        return Result.success(null);
    }
    
    @PutMapping("/{id}/default")
    public Result<Void> setDefaultAddress(@PathVariable Long id, @RequestHeader("X-User-Id") Long userId) {
        addressApplicationService.setDefaultAddress(id, userId);
        return Result.success(null);
    }
}
