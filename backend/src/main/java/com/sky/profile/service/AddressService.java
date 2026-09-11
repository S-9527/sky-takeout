package com.sky.profile.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

import com.sky.common.error.BusinessException;
import com.sky.profile.domain.ProfileErrorCode;
import com.sky.profile.domain.UserAddress;
import com.sky.profile.mapper.UserAddressMapper;

/**
 * 地址簿。只能读写本人的地址(R9),默认地址每顾客至多一条。
 *
 * <p>设置默认的写路径统一走"先清空其它默认 → 再置本条 → 加锁计数校验",三步在同一事务里;
 * 库层没有部分唯一索引,并发只能这样兜。
 */
@Service
public class AddressService {

    /** 契约 §2.4:每顾客地址上限 20。 */
    public static final int MAX_ADDRESSES = 20;

    private final UserAddressMapper userAddressMapper;

    public AddressService(UserAddressMapper userAddressMapper) {
        this.userAddressMapper = userAddressMapper;
    }

    /** 默认地址排最前,其余按创建时间倒序;同值用 id 升序保证顺序确定。 */
    public List<UserAddress> list(Long customerId) {
        return userAddressMapper.selectList(Wrappers.<UserAddress>lambdaQuery()
                .eq(UserAddress::getCustomerId, customerId)
                .orderByDesc(UserAddress::getIsDefault)
                .orderByDesc(UserAddress::getCreatedAt)
                .orderByAsc(UserAddress::getId));
    }

    /** 他人地址与不存在的地址同样返回 404,不泄露存在性。 */
    public UserAddress requireOwned(Long customerId, Long id) {
        UserAddress address = userAddressMapper.selectById(id);
        if (address == null || !customerId.equals(address.getCustomerId())) {
            throw new BusinessException(ProfileErrorCode.ADDRESS_NOT_FOUND);
        }
        return address;
    }

    @Transactional
    public UserAddress create(Long customerId, String consignee, String phone, String province,
                              String city, String district, String detail, String label, boolean isDefault) {
        Long existing = userAddressMapper.selectCount(
                Wrappers.<UserAddress>lambdaQuery().eq(UserAddress::getCustomerId, customerId));
        if (existing != null && existing >= MAX_ADDRESSES) {
            throw new BusinessException(ProfileErrorCode.ADDRESS_LIMIT_EXCEEDED);
        }
        if (isDefault) {
            clearOtherDefaults(customerId, null);
        }

        UserAddress address = new UserAddress();
        address.setCustomerId(customerId);
        address.setConsignee(consignee);
        address.setPhone(phone);
        address.setProvince(province);
        address.setCity(city);
        address.setDistrict(district);
        address.setDetail(detail);
        address.setLabel(label);
        address.setIsDefault(isDefault);
        userAddressMapper.insert(address);

        if (isDefault) {
            requireSingleDefault(customerId);
        }
        return requireOwned(customerId, address.getId());
    }

    @Transactional
    public UserAddress update(Long customerId, Long id, String consignee, String phone, String province,
                              String city, String district, String detail, String label, boolean isDefault) {
        requireOwned(customerId, id);
        if (isDefault) {
            clearOtherDefaults(customerId, id);
        }

        UserAddress update = new UserAddress();
        update.setId(id);
        update.setConsignee(consignee);
        update.setPhone(phone);
        update.setProvince(province);
        update.setCity(city);
        update.setDistrict(district);
        update.setDetail(detail);
        update.setLabel(label);
        update.setIsDefault(isDefault);
        userAddressMapper.updateById(update);

        if (isDefault) {
            requireSingleDefault(customerId);
        }
        return requireOwned(customerId, id);
    }

    /**
     * 真删除(D13)。历史订单不受影响——订单里存的是地址快照。
     *
     * <p>"地址被未完成订单引用"只是一个提示,不阻止删除,所以这里没有对应的错误码。
     */
    @Transactional
    public void delete(Long customerId, Long id) {
        requireOwned(customerId, id);
        userAddressMapper.deleteById(id);
    }

    /** 设为默认。已经是默认时同样成功(幂等)。 */
    @Transactional
    public void setDefault(Long customerId, Long id) {
        requireOwned(customerId, id);
        clearOtherDefaults(customerId, id);

        UserAddress update = new UserAddress();
        update.setId(id);
        update.setIsDefault(true);
        userAddressMapper.updateById(update);

        requireSingleDefault(customerId);
    }

    private void clearOtherDefaults(Long customerId, Long keepId) {
        LambdaUpdateWrapper<UserAddress> wrapper = Wrappers.<UserAddress>lambdaUpdate()
                .eq(UserAddress::getCustomerId, customerId)
                .eq(UserAddress::getIsDefault, true);
        if (keepId != null) {
            wrapper.ne(UserAddress::getId, keepId);
        }
        UserAddress update = new UserAddress();
        update.setIsDefault(false);
        userAddressMapper.update(update, wrapper);
    }

    private void requireSingleDefault(Long customerId) {
        if (userAddressMapper.countDefaultsForUpdate(customerId) > 1) {
            throw new BusinessException(ProfileErrorCode.ADDRESS_DEFAULT_DUPLICATED);
        }
    }
}
