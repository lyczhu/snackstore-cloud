package com.lawyus.snackstore.user.domain.address.repository;

import com.lawyus.snackstore.user.domain.address.model.entity.Address;

import java.util.List;
import java.util.Optional;

public interface AddressRepository {

    Address save(Address address);

    Optional<Address> findById(Long id);

    List<Address> findByUserId(Long userId);

    Optional<Address> findByIdAndUserId(Long id, Long userId);

    List<Address> findDefaultByUserId(Long userId);

    void deleteById(Long id);

    boolean existsByIdAndUserId(Long id, Long userId);
}