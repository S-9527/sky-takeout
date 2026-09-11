package com.sky.identity.service;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import com.sky.common.domain.EnableStatus;
import com.sky.common.error.BusinessException;
import com.sky.common.util.Times;
import com.sky.identity.domain.Customer;
import com.sky.identity.domain.IdentityErrorCode;
import com.sky.identity.gateway.WechatAuthClient;
import com.sky.identity.mapper.CustomerMapper;
import com.sky.security.Audience;
import com.sky.security.TokenService;

/** 顾客账号:微信登录、资料读写。 */
@Service
public class CustomerService {

    /** 顾客端角色常量。顾客没有角色分级,但令牌载荷需要一个 role。 */
    public static final String CUSTOMER_ROLE = "CUSTOMER";

    private final CustomerMapper customerMapper;
    private final WechatAuthClient wechatAuthClient;
    private final TokenService tokenService;

    public CustomerService(CustomerMapper customerMapper, WechatAuthClient wechatAuthClient, TokenService tokenService) {
        this.customerMapper = customerMapper;
        this.wechatAuthClient = wechatAuthClient;
        this.tokenService = tokenService;
    }

    public record LoginResult(Customer customer, TokenService.IssuedTokens tokens) {
    }

    /**
     * 微信登录:code 换 openid,首次登录自动建档。
     *
     * <p><b>刻意不加 {@code @Transactional}</b>:本方法最多写一条 insert 和一条 update,单条语句各自原子,
     * 不存在需要原子性的多语句约束。反过来,如果包在事务里,下面捕获唯一键冲突后就无法再查询
     * (事务已被标记为回滚),并发首登只能给用户报 409。
     */
    public LoginResult loginByWechat(String loginCode, String nickname, String avatarUrl) {
        String openid = wechatAuthClient.exchangeOpenid(loginCode);
        Customer customer = findOrCreate(openid, nickname, avatarUrl);

        if (!customer.isEnabled()) {
            throw new BusinessException(IdentityErrorCode.CUSTOMER_DISABLED);
        }

        Customer touch = new Customer();
        touch.setId(customer.getId());
        touch.setLastLoginAt(Times.nowLocal());
        customerMapper.updateById(touch);

        TokenService.IssuedTokens tokens = tokenService.issue(customer.getId(), Audience.CUSTOMER, CUSTOMER_ROLE);
        return new LoginResult(customer, tokens);
    }

    public Customer requireById(Long id) {
        Customer customer = customerMapper.selectById(id);
        if (customer == null) {
            throw new BusinessException(IdentityErrorCode.CUSTOMER_NOT_FOUND);
        }
        return customer;
    }

    @Transactional
    public Customer updateProfile(Long id, String nickname, String avatarUrl, String phone) {
        requireById(id);
        Customer update = new Customer();
        update.setId(id);
        update.setNickname(StringUtils.hasText(nickname) ? nickname : null);
        update.setAvatarUrl(StringUtils.hasText(avatarUrl) ? avatarUrl : null);
        update.setPhone(StringUtils.hasText(phone) ? phone : null);
        customerMapper.updateById(update);
        return requireById(id);
    }

    /**
     * 找到既有顾客或新建一个。
     *
     * <p>并发首次登录(用户连点、小程序重试)会同时走到 insert,由 openid 唯一键兜底;
     * 冲突时说明另一个请求刚建好,直接读出来即可,对用户完全无感。
     */
    private Customer findOrCreate(String openid, String nickname, String avatarUrl) {
        Customer existing = findByOpenid(openid);
        if (existing != null) {
            return existing;
        }
        try {
            Customer created = new Customer();
            created.setOpenid(openid);
            created.setNickname(StringUtils.hasText(nickname) ? nickname : "微信用户");
            created.setAvatarUrl(avatarUrl);
            created.setStatus(EnableStatus.ENABLED);
            created.setLastLoginAt(Times.nowLocal());
            customerMapper.insert(created);
            return created;
        } catch (DuplicateKeyException ex) {
            Customer concurrent = findByOpenid(openid);
            if (concurrent == null) {
                throw new BusinessException(IdentityErrorCode.CUSTOMER_DUPLICATE_OPENID);
            }
            return concurrent;
        }
    }

    private Customer findByOpenid(String openid) {
        return customerMapper.selectOne(Wrappers.<Customer>lambdaQuery().eq(Customer::getOpenid, openid));
    }
}
