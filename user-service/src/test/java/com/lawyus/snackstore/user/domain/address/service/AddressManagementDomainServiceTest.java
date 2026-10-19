package com.lawyus.snackstore.user.domain.address.service;

import com.lawyus.snackstore.user.domain.address.model.entity.Address;
import com.lawyus.snackstore.user.domain.address.model.valueobject.AddressDetail;
import com.lawyus.snackstore.user.domain.address.model.valueobject.ReceiverInfo;
import com.lawyus.snackstore.user.domain.address.repository.AddressRepository;
import com.lawyus.snackstore.user.domain.common.event.DomainEventPublisher;
import com.lawyus.snackstore.user.exception.BusinessException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 收货地址领域服务的特征化测试。
 *
 * <p>核心不变量是「一个用户最多一个默认地址」：设为默认前必须先清旧默认，
 * 而删掉或取消默认后必须自动顶上一条，否则用户下单时没有默认地址可选。
 * 另外锁住单用户地址上限与跨用户越权访问。
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("AddressManagementDomainService 默认地址不变量")
class AddressManagementDomainServiceTest {

    private static final Long USER_ID = 7L;

    @Mock
    private AddressRepository addressRepository;

    @Mock
    private DomainEventPublisher eventPublisher;

    private AddressManagementDomainService service;

    @BeforeEach
    void setUp() {
        service = new AddressManagementDomainService(addressRepository, eventPublisher);
    }

    private static ReceiverInfo receiver() {
        return ReceiverInfo.of("张三", "13800000000");
    }

    private static AddressDetail detail() {
        return AddressDetail.of("浙江省", "杭州市", "西湖区", "文一西路 1 号");
    }

    private static Address address(long id, boolean isDefault) {
        Address a = Address.create(USER_ID, receiver(), detail(), isDefault);
        a.assignId(id);
        return a;
    }

    @Test
    @DisplayName("地址数量达上限 20 时拒绝创建，且不落库")
    void createIsRejectedAtLimit() {
        when(addressRepository.countByUserId(USER_ID)).thenReturn(20L);

        assertThatThrownBy(() -> service.createAddress(USER_ID, receiver(), detail(), false))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("上限");

        verify(addressRepository, never()).save(any());
    }

    @Test
    @DisplayName("新建为默认地址：先清掉旧默认再保存（顺序颠倒会出现两个默认）")
    void creatingDefaultClearsPreviousDefaultFirst() {
        when(addressRepository.countByUserId(USER_ID)).thenReturn(1L);
        when(addressRepository.save(any(Address.class))).thenAnswer(inv -> inv.getArgument(0));

        service.createAddress(USER_ID, receiver(), detail(), true);

        InOrder order = inOrder(addressRepository);
        order.verify(addressRepository).clearDefaultsByUserId(USER_ID);
        order.verify(addressRepository).save(any(Address.class));
        verify(eventPublisher).publishAll(any());
    }

    @Test
    @DisplayName("新建非默认地址不动现有默认")
    void creatingNonDefaultDoesNotClearDefaults() {
        when(addressRepository.countByUserId(USER_ID)).thenReturn(1L);
        when(addressRepository.save(any(Address.class))).thenAnswer(inv -> inv.getArgument(0));

        service.createAddress(USER_ID, receiver(), detail(), false);

        verify(addressRepository, never()).clearDefaultsByUserId(anyLong());
    }

    @Test
    @DisplayName("越权访问他人地址：按 id+userId 查不到即报错，不暴露存在性")
    void cannotTouchAnotherUsersAddress() {
        when(addressRepository.findByIdAndUserId(3L, USER_ID)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.deleteAddress(3L, USER_ID))
                .isInstanceOf(BusinessException.class)
                .hasMessage("地址不存在");

        verify(addressRepository, never()).deleteById(anyLong());
    }

    @Test
    @DisplayName("取消默认后若用户已无默认地址，自动把最新一条提升为默认")
    void unsettingDefaultPromotesLatestWhenNoneLeft() {
        Address target = address(3L, true);
        when(addressRepository.findByIdAndUserId(3L, USER_ID)).thenReturn(Optional.of(target));
        when(addressRepository.save(any(Address.class))).thenAnswer(inv -> inv.getArgument(0));
        when(addressRepository.findByUserId(USER_ID)).thenReturn(List.of(target));
        Address latest = address(4L, false);
        when(addressRepository.findLatestByUserId(USER_ID)).thenReturn(Optional.of(latest));

        service.updateAddress(3L, USER_ID, receiver(), detail(), false);

        assertThat(latest.isDefault()).isTrue();
        verify(addressRepository, times(2)).save(any(Address.class));
    }

    @Test
    @DisplayName("取消默认但仍有其他默认地址时不做提升")
    void noPromotionWhenAnotherDefaultRemains() {
        Address target = address(3L, true);
        Address otherDefault = address(5L, true);
        when(addressRepository.findByIdAndUserId(3L, USER_ID)).thenReturn(Optional.of(target));
        when(addressRepository.save(any(Address.class))).thenAnswer(inv -> inv.getArgument(0));
        when(addressRepository.findByUserId(USER_ID)).thenReturn(List.of(target, otherDefault));

        service.updateAddress(3L, USER_ID, receiver(), detail(), false);

        verify(addressRepository, never()).findLatestByUserId(anyLong());
        verify(addressRepository, times(1)).save(any(Address.class));
    }

    @Test
    @DisplayName("删除默认地址后自动提升最新一条")
    void deletingDefaultPromotesLatest() {
        when(addressRepository.findByIdAndUserId(3L, USER_ID))
                .thenReturn(Optional.of(address(3L, true)));
        when(addressRepository.findLatestByUserId(USER_ID))
                .thenReturn(Optional.of(address(4L, false)));

        service.deleteAddress(3L, USER_ID);

        verify(addressRepository).deleteById(3L);
        verify(addressRepository).save(any(Address.class));
    }

    @Test
    @DisplayName("删除非默认地址不触发提升")
    void deletingNonDefaultDoesNotPromote() {
        when(addressRepository.findByIdAndUserId(6L, USER_ID))
                .thenReturn(Optional.of(address(6L, false)));

        service.deleteAddress(6L, USER_ID);

        verify(addressRepository).deleteById(6L);
        verify(addressRepository, never()).findLatestByUserId(anyLong());
        verify(addressRepository, never()).save(any(Address.class));
    }

    @Test
    @DisplayName("设为默认：先清旧默认，再置位并保存")
    void setDefaultClearsThenMarks() {
        Address target = address(3L, false);
        when(addressRepository.findByIdAndUserId(3L, USER_ID)).thenReturn(Optional.of(target));
        when(addressRepository.save(target)).thenReturn(target);

        Address result = service.setDefaultAddress(3L, USER_ID);

        InOrder order = inOrder(addressRepository);
        order.verify(addressRepository).clearDefaultsByUserId(USER_ID);
        order.verify(addressRepository).save(target);
        assertThat(result.isDefault()).isTrue();
    }
}
