package com.sky.profile.service;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.util.List;

import com.sky.common.error.BusinessException;
import com.sky.profile.domain.ProfileErrorCode;
import com.sky.profile.domain.UserAddress;
import com.sky.profile.mapper.UserAddressMapper;
import com.sky.testsupport.TableInfoTestSupport;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class AddressServiceTest {

    private static final long CUSTOMER = 7L;

    private final UserAddressMapper userAddressMapper = mock(UserAddressMapper.class);
    private final AddressService addressService = new AddressService(userAddressMapper);

    @BeforeAll
    static void registerTableInfo() {
        // list / clearOtherDefaults / selectCount 都要构 lambda wrapper,单测需要显式注册 TableInfo
        TableInfoTestSupport.register(UserAddress.class);
    }

    private static UserAddress address(long id, long customerId, boolean isDefault) {
        UserAddress address = new UserAddress();
        address.setId(id);
        address.setCustomerId(customerId);
        address.setConsignee("张三");
        address.setPhone("13800138000");
        address.setProvince("北京市");
        address.setCity("北京市");
        address.setDistrict("朝阳区");
        address.setDetail("望京街道 1 号院");
        address.setIsDefault(isDefault);
        return address;
    }

    private void insertAssignsId(long id) {
        when(userAddressMapper.insert(any(UserAddress.class))).thenAnswer(invocation -> {
            ((UserAddress) invocation.getArgument(0)).setId(id);
            return 1;
        });
    }

    // ---------------------------------------------------------------- 列表与读取

    @Test
    void listReturnsMapperResultInGivenOrder() {
        when(userAddressMapper.selectList(any())).thenReturn(List.of(address(2L, CUSTOMER, true)));

        assertThat(addressService.list(CUSTOMER)).hasSize(1);
        verify(userAddressMapper).selectList(any());
    }

    @Test
    void requireOwnedReturnsAddress() {
        when(userAddressMapper.selectById(1L)).thenReturn(address(1L, CUSTOMER, false));

        assertThat(addressService.requireOwned(CUSTOMER, 1L).getConsignee()).isEqualTo("张三");
    }

    /** R9:他人地址与不存在的地址返回同一个错误码。 */
    @Test
    void requireOwnedHidesOtherCustomersAddresses() {
        when(userAddressMapper.selectById(1L)).thenReturn(address(1L, 999L, false));
        assertThatThrownBy(() -> addressService.requireOwned(CUSTOMER, 1L))
                .isInstanceOfSatisfying(BusinessException.class,
                        ex -> assertThat(ex.errorCode()).isEqualTo(ProfileErrorCode.ADDRESS_NOT_FOUND));

        when(userAddressMapper.selectById(404L)).thenReturn(null);
        assertThatThrownBy(() -> addressService.requireOwned(CUSTOMER, 404L))
                .isInstanceOfSatisfying(BusinessException.class,
                        ex -> assertThat(ex.errorCode()).isEqualTo(ProfileErrorCode.ADDRESS_NOT_FOUND));
    }

    // ---------------------------------------------------------------- 新增

    @Test
    void createStoresAllFieldsAndReturnsPersistedRow() {
        when(userAddressMapper.selectCount(any())).thenReturn(0L);
        insertAssignsId(501L);
        when(userAddressMapper.selectById(501L)).thenReturn(address(501L, CUSTOMER, false));

        UserAddress created = addressService.create(CUSTOMER, "张三", "13800138000", "北京市", "北京市",
                "朝阳区", "望京街道 1 号院", "家", false);

        ArgumentCaptor<UserAddress> inserted = ArgumentCaptor.forClass(UserAddress.class);
        verify(userAddressMapper).insert(inserted.capture());
        assertThat(inserted.getValue().getCustomerId()).isEqualTo(CUSTOMER);
        assertThat(inserted.getValue().getLabel()).isEqualTo("家");
        assertThat(inserted.getValue().getIsDefault()).isFalse();
        assertThat(created.getId()).isEqualTo(501L);
        // 非默认地址不需要清理其它默认,也不做加锁计数
        verify(userAddressMapper, never()).countDefaultsForUpdate(any());
    }

    @Test
    void createDefaultClearsOtherDefaultsAndVerifiesUniqueness() {
        when(userAddressMapper.selectCount(any())).thenReturn(0L);
        insertAssignsId(501L);
        when(userAddressMapper.countDefaultsForUpdate(CUSTOMER)).thenReturn(1);
        when(userAddressMapper.selectById(501L)).thenReturn(address(501L, CUSTOMER, true));

        addressService.create(CUSTOMER, "张三", "13800138000", "北京市", "北京市",
                "朝阳区", "望京街道 1 号院", null, true);

        verify(userAddressMapper).update(any(UserAddress.class), any());
        verify(userAddressMapper).countDefaultsForUpdate(CUSTOMER);
    }

    @Test
    void createRejectsWhenLimitReached() {
        when(userAddressMapper.selectCount(any())).thenReturn((long) AddressService.MAX_ADDRESSES);

        assertThatThrownBy(() -> addressService.create(CUSTOMER, "张三", "13800138000", "北京市", "北京市",
                "朝阳区", "望京街道 1 号院", null, false))
                .isInstanceOfSatisfying(BusinessException.class,
                        ex -> assertThat(ex.errorCode()).isEqualTo(ProfileErrorCode.ADDRESS_LIMIT_EXCEEDED));
        verify(userAddressMapper, never()).insert(any(UserAddress.class));
    }

    @Test
    void createAllowsExactlyAtLimitBoundary() {
        when(userAddressMapper.selectCount(any())).thenReturn((long) AddressService.MAX_ADDRESSES - 1);
        insertAssignsId(520L);
        when(userAddressMapper.selectById(520L)).thenReturn(address(520L, CUSTOMER, false));

        addressService.create(CUSTOMER, "张三", "13800138000", "北京市", "北京市",
                "朝阳区", "望京街道 1 号院", null, false);

        verify(userAddressMapper).insert(any(UserAddress.class));
    }

    // ---------------------------------------------------------------- 编辑

    @Test
    void updateWritesEveryField() {
        when(userAddressMapper.selectById(501L)).thenReturn(address(501L, CUSTOMER, false));

        addressService.update(CUSTOMER, 501L, "李四", "13900139000", "上海市", "上海市",
                "浦东新区", "世纪大道 1 号", "公司", false);

        ArgumentCaptor<UserAddress> update = ArgumentCaptor.forClass(UserAddress.class);
        verify(userAddressMapper).updateById(update.capture());
        assertThat(update.getValue().getConsignee()).isEqualTo("李四");
        assertThat(update.getValue().getLabel()).isEqualTo("公司");
        assertThat(update.getValue().getIsDefault()).isFalse();
    }

    @Test
    void updateToDefaultClearsOtherDefaults() {
        when(userAddressMapper.selectById(501L)).thenReturn(address(501L, CUSTOMER, false));
        when(userAddressMapper.countDefaultsForUpdate(CUSTOMER)).thenReturn(1);

        addressService.update(CUSTOMER, 501L, "李四", "13900139000", "上海市", "上海市",
                "浦东新区", "世纪大道 1 号", null, true);

        verify(userAddressMapper).update(any(UserAddress.class), any());
        verify(userAddressMapper).countDefaultsForUpdate(CUSTOMER);
    }

    @Test
    void updateRejectsUnknownAddress() {
        when(userAddressMapper.selectById(404L)).thenReturn(null);

        assertThatThrownBy(() -> addressService.update(CUSTOMER, 404L, "李四", "13900139000", "上海市",
                "上海市", "浦东新区", "世纪大道 1 号", null, false))
                .isInstanceOfSatisfying(BusinessException.class,
                        ex -> assertThat(ex.errorCode()).isEqualTo(ProfileErrorCode.ADDRESS_NOT_FOUND));
        verify(userAddressMapper, never()).updateById(any(UserAddress.class));
    }

    // ---------------------------------------------------------------- 删除

    @Test
    void deleteRemovesOwnedAddress() {
        when(userAddressMapper.selectById(501L)).thenReturn(address(501L, CUSTOMER, false));

        addressService.delete(CUSTOMER, 501L);

        verify(userAddressMapper).deleteById(501L);
    }

    @Test
    void deleteRejectsUnknownAddress() {
        when(userAddressMapper.selectById(404L)).thenReturn(null);

        assertThatThrownBy(() -> addressService.delete(CUSTOMER, 404L))
                .isInstanceOfSatisfying(BusinessException.class,
                        ex -> assertThat(ex.errorCode()).isEqualTo(ProfileErrorCode.ADDRESS_NOT_FOUND));
        verify(userAddressMapper, never()).deleteById(any(Long.class));
    }

    // ---------------------------------------------------------------- 设为默认

    @Test
    void setDefaultClearsOthersAndMarksThisOne() {
        when(userAddressMapper.selectById(501L)).thenReturn(address(501L, CUSTOMER, false));
        when(userAddressMapper.countDefaultsForUpdate(CUSTOMER)).thenReturn(1);

        addressService.setDefault(CUSTOMER, 501L);

        verify(userAddressMapper).update(any(UserAddress.class), any());
        ArgumentCaptor<UserAddress> update = ArgumentCaptor.forClass(UserAddress.class);
        verify(userAddressMapper).updateById(update.capture());
        assertThat(update.getValue().getId()).isEqualTo(501L);
        assertThat(update.getValue().getIsDefault()).isTrue();
        verify(userAddressMapper).countDefaultsForUpdate(CUSTOMER);
    }

    /** 并发设置默认时,加锁计数会发现两条默认 → 409,而不是把两条默认留在库里。 */
    @Test
    void setDefaultReportsConflictWhenTwoDefaultsExist() {
        when(userAddressMapper.selectById(501L)).thenReturn(address(501L, CUSTOMER, false));
        when(userAddressMapper.countDefaultsForUpdate(CUSTOMER)).thenReturn(2);

        assertThatThrownBy(() -> addressService.setDefault(CUSTOMER, 501L))
                .isInstanceOfSatisfying(BusinessException.class,
                        ex -> assertThat(ex.errorCode()).isEqualTo(ProfileErrorCode.ADDRESS_DEFAULT_DUPLICATED));
    }

    @Test
    void setDefaultRejectsUnknownAddress() {
        when(userAddressMapper.selectById(404L)).thenReturn(null);

        assertThatThrownBy(() -> addressService.setDefault(CUSTOMER, 404L))
                .isInstanceOfSatisfying(BusinessException.class,
                        ex -> assertThat(ex.errorCode()).isEqualTo(ProfileErrorCode.ADDRESS_NOT_FOUND));
        verify(userAddressMapper, never()).updateById(any(UserAddress.class));
    }

    @Test
    void setDefaultIsIdempotentWhenAlreadyDefault() {
        when(userAddressMapper.selectById(501L)).thenReturn(address(501L, CUSTOMER, true));
        when(userAddressMapper.countDefaultsForUpdate(CUSTOMER)).thenReturn(1);

        addressService.setDefault(CUSTOMER, 501L);

        verify(userAddressMapper, times(1)).updateById(any(UserAddress.class));
    }
}
