package com.sky.profile.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import com.sky.profile.domain.UserAddress;

@Mapper
public interface UserAddressMapper extends BaseMapper<UserAddress> {

    /**
     * 加锁统计该顾客当前的默认地址条数。
     *
     * <p>普通 SELECT 在 REPEATABLE READ 下读的是事务快照,看不到并发事务刚提交的默认地址;
     * {@code FOR UPDATE} 是当前读,能看见,于是"并发设置默认"才能被识别成 409 而不是留下两条默认。
     */
    @Select("SELECT COUNT(*) FROM `user_address` WHERE `customer_id` = #{customerId} AND `is_default` = 1 FOR UPDATE")
    int countDefaultsForUpdate(@Param("customerId") Long customerId);
}
