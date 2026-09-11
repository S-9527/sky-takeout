package com.sky.payment.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;

import com.sky.payment.domain.Payment;

@Mapper
public interface PaymentMapper extends BaseMapper<Payment> {
}
