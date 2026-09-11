package com.sky.profile.api;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;

import java.util.List;

import com.sky.common.error.BusinessException;
import com.sky.common.error.CommonErrorCode;
import com.sky.profile.api.dto.UserAddressCreateRequest;
import com.sky.profile.api.dto.UserAddressResponse;
import com.sky.profile.api.dto.UserAddressUpdateRequest;
import com.sky.profile.domain.UserAddress;
import com.sky.profile.service.AddressService;
import com.sky.security.Audience;
import com.sky.security.CurrentPrincipal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class AddressControllerTest {

    private static final long CUSTOMER = 7L;

    private final AddressService addressService = mock(AddressService.class);
    private final AddressController controller = new AddressController(addressService);

    @AfterEach
    void clearContext() {
        SecurityContextHolder.clearContext();
    }

    private static void actingAsCustomer() {
        SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken(
                new CurrentPrincipal(CUSTOMER, Audience.CUSTOMER, "CUSTOMER"), null));
    }

    private static UserAddress address(boolean isDefault) {
        UserAddress address = new UserAddress();
        address.setId(501L);
        address.setCustomerId(CUSTOMER);
        address.setConsignee("张三");
        address.setPhone("13800138000");
        address.setProvince("北京市");
        address.setCity("北京市");
        address.setDistrict("朝阳区");
        address.setDetail("望京街道 1 号院");
        address.setLabel("家");
        address.setIsDefault(isDefault);
        return address;
    }

    @Test
    void listMapsDefaultsToFlagIntegers() {
        actingAsCustomer();
        when(addressService.list(CUSTOMER)).thenReturn(List.of(address(true), address(false)));

        List<UserAddressResponse> result = controller.list();

        assertThat(result).hasSize(2);
        assertThat(result.get(0).isDefault()).isEqualTo(1);
        assertThat(result.get(1).isDefault()).isZero();
        assertThat(result.get(0).customerId()).isEqualTo(CUSTOMER);
    }

    @Test
    void createPassesFlagAndReturns201Body() {
        actingAsCustomer();
        when(addressService.create(anyLong(), any(), any(), any(), any(), any(), any(), any(), anyBoolean()))
                .thenReturn(address(true));

        UserAddressResponse response = controller.create(new UserAddressCreateRequest(
                "张三", "13800138000", "北京市", "北京市", "朝阳区", "望京街道 1 号院", "家", 1));

        assertThat(response.id()).isEqualTo(501L);
        assertThat(response.isDefault()).isEqualTo(1);
        verify(addressService).create(CUSTOMER, "张三", "13800138000", "北京市", "北京市",
                "朝阳区", "望京街道 1 号院", "家", true);
    }

    @Test
    void createWithoutFlagDefaultsToNonDefault() {
        actingAsCustomer();
        when(addressService.create(anyLong(), any(), any(), any(), any(), any(), any(), any(), anyBoolean()))
                .thenReturn(address(false));

        controller.create(new UserAddressCreateRequest(
                "张三", "13800138000", "北京市", "北京市", "朝阳区", "望京街道 1 号院", null, null));

        verify(addressService).create(CUSTOMER, "张三", "13800138000", "北京市", "北京市",
                "朝阳区", "望京街道 1 号院", null, false);
    }

    /** 契约里 isDefault 是 1/0;传 2 必须落到统一错误体,而不是被静默当成 false。 */
    @Test
    void createRejectsIllegalFlagValue() {
        actingAsCustomer();

        assertThatThrownBy(() -> controller.create(new UserAddressCreateRequest(
                "张三", "13800138000", "北京市", "北京市", "朝阳区", "望京街道 1 号院", null, 2)))
                .isInstanceOfSatisfying(BusinessException.class, ex -> {
                    assertThat(ex.errorCode()).isEqualTo(CommonErrorCode.COMMON_VALIDATION_FAILED);
                    assertThat(ex.details()).isNotEmpty();
                });
    }

    @Test
    void getByIdUsesOwnershipCheck() {
        actingAsCustomer();
        when(addressService.requireOwned(CUSTOMER, 501L)).thenReturn(address(false));

        assertThat(controller.getById(501L).consignee()).isEqualTo("张三");
    }

    @Test
    void updatePassesFlagThrough() {
        actingAsCustomer();
        when(addressService.update(anyLong(), anyLong(), any(), any(), any(), any(), any(), any(), any(), anyBoolean()))
                .thenReturn(address(true));

        controller.update(501L, new UserAddressUpdateRequest(
                "李四", "13900139000", "上海市", "上海市", "浦东新区", "世纪大道 1 号", "公司", 1));

        verify(addressService).update(CUSTOMER, 501L, "李四", "13900139000", "上海市", "上海市",
                "浦东新区", "世纪大道 1 号", "公司", true);
    }

    @Test
    void deleteDelegates() {
        actingAsCustomer();

        controller.delete(501L);

        verify(addressService).delete(CUSTOMER, 501L);
    }

    @Test
    void setDefaultDelegates() {
        actingAsCustomer();

        controller.setDefault(501L);

        verify(addressService).setDefault(eq(CUSTOMER), eq(501L));
    }
}
