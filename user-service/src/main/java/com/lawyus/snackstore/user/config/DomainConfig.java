package com.lawyus.snackstore.user.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import com.lawyus.snackstore.user.domain.address.repository.AddressRepository;
import com.lawyus.snackstore.user.domain.address.service.AddressManagementDomainService;
import com.lawyus.snackstore.user.domain.common.event.DomainEventPublisher;
import com.lawyus.snackstore.user.domain.user.port.LoginLockPort;
import com.lawyus.snackstore.user.domain.user.port.PasswordEncoder;
import com.lawyus.snackstore.user.domain.user.port.VerificationCodePort;
import com.lawyus.snackstore.user.domain.user.repository.UserRepository;
import com.lawyus.snackstore.user.domain.user.service.UserAuthenticationDomainService;
import com.lawyus.snackstore.user.domain.user.service.UserManagementDomainService;

@Configuration
public class DomainConfig {

    @Bean
    public UserAuthenticationDomainService userAuthenticationDomainService(UserRepository userRepository,
                                                                            PasswordEncoder passwordEncoder,
                                                                            DomainEventPublisher eventPublisher,
                                                                            VerificationCodePort verificationCodePort,
                                                                            LoginLockPort loginLockPort) {
        return new UserAuthenticationDomainService(userRepository, passwordEncoder, eventPublisher, verificationCodePort, loginLockPort);
    }

    @Bean
    public UserManagementDomainService userManagementDomainService(UserRepository userRepository) {
        return new UserManagementDomainService(userRepository);
    }

    @Bean
    public AddressManagementDomainService addressManagementDomainService(AddressRepository addressRepository,
                                                                          DomainEventPublisher eventPublisher) {
        return new AddressManagementDomainService(addressRepository, eventPublisher);
    }
}