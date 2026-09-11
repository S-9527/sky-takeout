package com.sky.identity.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.dao.DuplicateKeyException;

import com.sky.common.domain.EnableStatus;
import com.sky.common.error.BusinessException;
import com.sky.identity.domain.Customer;
import com.sky.identity.domain.IdentityErrorCode;
import com.sky.identity.gateway.WechatAuthClient;
import com.sky.identity.mapper.CustomerMapper;
import com.sky.security.Audience;
import com.sky.security.TokenService;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class CustomerServiceTest {

    private final CustomerMapper customerMapper = mock(CustomerMapper.class);
    private final WechatAuthClient wechatAuthClient = mock(WechatAuthClient.class);
    private final TokenService tokenService = mock(TokenService.class);

    private final CustomerService customerService =
            new CustomerService(customerMapper, wechatAuthClient, tokenService);

    @BeforeEach
    void defaultTokenPair() {
        when(tokenService.issue(anyLong(), any(), any()))
                .thenReturn(new TokenService.IssuedTokens("access", 7200, "refresh", 604800));
        when(wechatAuthClient.exchangeOpenid("code-1")).thenReturn("openid-1");
    }

    private static Customer customer(long id, EnableStatus status) {
        Customer customer = new Customer();
        customer.setId(id);
        customer.setOpenid("openid-1");
        customer.setNickname("微信用户");
        customer.setStatus(status);
        return customer;
    }

    // ---------------------------------------------------------------- 微信登录

    @Test
    void firstLoginCreatesCustomerAndIssuesTokens() {
        when(customerMapper.selectOne(any())).thenReturn(null);
        when(customerMapper.insert(any(Customer.class))).thenAnswer(invocation -> {
            Customer inserted = invocation.getArgument(0);
            inserted.setId(100L);
            return 1;
        });

        CustomerService.LoginResult result = customerService.loginByWechat("code-1", "小明", "http://avatar/1.png");

        ArgumentCaptor<Customer> inserted = ArgumentCaptor.forClass(Customer.class);
        verify(customerMapper).insert(inserted.capture());
        assertThat(inserted.getValue().getOpenid()).isEqualTo("openid-1");
        assertThat(inserted.getValue().getNickname()).isEqualTo("小明");
        assertThat(inserted.getValue().getStatus()).isEqualTo(EnableStatus.ENABLED);
        assertThat(result.customer().getId()).isEqualTo(100L);
        verify(tokenService).issue(100L, Audience.CUSTOMER, CustomerService.CUSTOMER_ROLE);
    }

    @Test
    void firstLoginWithoutNicknameFallsBackToPlaceholder() {
        when(customerMapper.selectOne(any())).thenReturn(null);
        when(customerMapper.insert(any(Customer.class))).thenAnswer(invocation -> {
            ((Customer) invocation.getArgument(0)).setId(101L);
            return 1;
        });

        customerService.loginByWechat("code-1", "   ", null);

        ArgumentCaptor<Customer> inserted = ArgumentCaptor.forClass(Customer.class);
        verify(customerMapper).insert(inserted.capture());
        assertThat(inserted.getValue().getNickname()).isEqualTo("微信用户");
    }

    @Test
    void existingCustomerIsReusedAndLastLoginIsTouched() {
        when(customerMapper.selectOne(any())).thenReturn(customer(7L, EnableStatus.ENABLED));

        CustomerService.LoginResult result = customerService.loginByWechat("code-1", null, null);

        assertThat(result.customer().getId()).isEqualTo(7L);
        verify(customerMapper, never()).insert(any(Customer.class));
        ArgumentCaptor<Customer> touch = ArgumentCaptor.forClass(Customer.class);
        verify(customerMapper).updateById(touch.capture());
        assertThat(touch.getValue().getLastLoginAt()).isNotNull();
    }

    @Test
    void disabledCustomerCannotLogin() {
        when(customerMapper.selectOne(any())).thenReturn(customer(7L, EnableStatus.DISABLED));

        assertThatThrownBy(() -> customerService.loginByWechat("code-1", null, null))
                .isInstanceOfSatisfying(BusinessException.class,
                        ex -> assertThat(ex.errorCode()).isEqualTo(IdentityErrorCode.CUSTOMER_DISABLED));
        verify(tokenService, never()).issue(anyLong(), any(), any());
    }

    /** 并发首登:insert 撞唯一键后必须把另一个请求刚建好的顾客读回来,对用户无感。 */
    @Test
    void concurrentFirstLoginFallsBackToTheRowCreatedByTheOtherRequest() {
        Customer concurrent = customer(200L, EnableStatus.ENABLED);
        when(customerMapper.selectOne(any())).thenReturn(null, concurrent);
        when(customerMapper.insert(any(Customer.class))).thenThrow(new DuplicateKeyException("uk_customer_openid"));

        CustomerService.LoginResult result = customerService.loginByWechat("code-1", null, null);

        assertThat(result.customer().getId()).isEqualTo(200L);
        verify(tokenService).issue(200L, Audience.CUSTOMER, CustomerService.CUSTOMER_ROLE);
    }

    @Test
    void concurrentFirstLoginWithoutReadableRowReportsConflict() {
        when(customerMapper.selectOne(any())).thenReturn(null);
        when(customerMapper.insert(any(Customer.class))).thenThrow(new DuplicateKeyException("uk_customer_openid"));

        assertThatThrownBy(() -> customerService.loginByWechat("code-1", null, null))
                .isInstanceOfSatisfying(BusinessException.class,
                        ex -> assertThat(ex.errorCode()).isEqualTo(IdentityErrorCode.CUSTOMER_DUPLICATE_OPENID));
    }

    // ---------------------------------------------------------------- 资料

    @Test
    void requireByIdReturnsCustomerOr404() {
        when(customerMapper.selectById(7L)).thenReturn(customer(7L, EnableStatus.ENABLED));
        assertThat(customerService.requireById(7L).getId()).isEqualTo(7L);

        when(customerMapper.selectById(404L)).thenReturn(null);
        assertThatThrownBy(() -> customerService.requireById(404L))
                .isInstanceOfSatisfying(BusinessException.class,
                        ex -> assertThat(ex.errorCode()).isEqualTo(IdentityErrorCode.CUSTOMER_NOT_FOUND));
    }

    /** 空串等同于"没填":不能把空字符串写进库,否则前端要处理两种"空"。 */
    @Test
    void updateProfileTurnsBlankFieldsIntoNulls() {
        when(customerMapper.selectById(7L)).thenReturn(customer(7L, EnableStatus.ENABLED));

        customerService.updateProfile(7L, "   ", "", "13900000000");

        ArgumentCaptor<Customer> update = ArgumentCaptor.forClass(Customer.class);
        verify(customerMapper).updateById(update.capture());
        assertThat(update.getValue().getNickname()).isNull();
        assertThat(update.getValue().getAvatarUrl()).isNull();
        assertThat(update.getValue().getPhone()).isEqualTo("13900000000");
    }

    @Test
    void updateProfileWritesProvidedValues() {
        when(customerMapper.selectById(7L)).thenReturn(customer(7L, EnableStatus.ENABLED));

        customerService.updateProfile(7L, "小明", "http://avatar/2.png", null);

        ArgumentCaptor<Customer> update = ArgumentCaptor.forClass(Customer.class);
        verify(customerMapper).updateById(update.capture());
        assertThat(update.getValue().getNickname()).isEqualTo("小明");
        assertThat(update.getValue().getAvatarUrl()).isEqualTo("http://avatar/2.png");
        assertThat(update.getValue().getPhone()).isNull();
    }

    @Test
    void updateProfileRejectsUnknownCustomerWithoutWriting() {
        when(customerMapper.selectById(404L)).thenReturn(null);

        assertThatThrownBy(() -> customerService.updateProfile(404L, "小明", null, null))
                .isInstanceOfSatisfying(BusinessException.class,
                        ex -> assertThat(ex.errorCode()).isEqualTo(IdentityErrorCode.CUSTOMER_NOT_FOUND));
        verify(customerMapper, never()).updateById(any(Customer.class));
    }
}
