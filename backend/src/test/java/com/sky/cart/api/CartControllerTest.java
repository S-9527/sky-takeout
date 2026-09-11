package com.sky.cart.api;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;

import java.util.List;
import java.util.Optional;

import com.sky.cart.api.dto.CartItemAddRequest;
import com.sky.cart.api.dto.CartItemQuantityRequest;
import com.sky.cart.api.dto.CartItemViewResponse;
import com.sky.cart.api.dto.CartViewResponse;
import com.sky.cart.domain.FlavorChoice;
import com.sky.cart.domain.ItemType;
import com.sky.cart.service.CartService;
import com.sky.security.Audience;
import com.sky.security.CurrentPrincipal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class CartControllerTest {

    private static final long CUSTOMER = 7L;

    private final CartService cartService = mock(CartService.class);
    private final CartController controller = new CartController(cartService);

    @AfterEach
    void clearContext() {
        SecurityContextHolder.clearContext();
    }

    private static void actingAsCustomer() {
        SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken(
                new CurrentPrincipal(CUSTOMER, Audience.CUSTOMER, "CUSTOMER"), null));
    }

    private static CartService.CartItemSnapshot snapshot(long id, int quantity) {
        return new CartService.CartItemSnapshot(id, ItemType.DISH, 101L, null,
                "宫保鸡丁", "/files/dish/101.jpg", 3800L, quantity, 3800L * quantity,
                List.of(new FlavorChoice("辣度", "微辣")), true, null);
    }

    @Test
    void listMapsGroupedSummaryAndTotals() {
        actingAsCustomer();
        when(cartService.summary(CUSTOMER)).thenReturn(new CartService.CartSummary(
                List.of(new CartService.CartGroup(1L, "川湘菜", List.of(snapshot(900L, 2)))),
                2, 7600L));

        CartViewResponse response = controller.list();

        assertThat(response.totalQuantity()).isEqualTo(2);
        assertThat(response.totalAmountCents()).isEqualTo(7600L);
        assertThat(response.groups()).hasSize(1);
        assertThat(response.groups().get(0).categoryName()).isEqualTo("川湘菜");
        CartItemViewResponse item = response.groups().get(0).items().get(0);
        assertThat(item.itemType()).isEqualTo("DISH");
        assertThat(item.name()).isEqualTo("宫保鸡丁");
        assertThat(item.amountCents()).isEqualTo(7600L);
        assertThat(item.available()).isTrue();
    }

    @Test
    void addReturns201WhenRowWasCreated() {
        actingAsCustomer();
        when(cartService.add(anyLong(), any(), any(), any(), anyInt(), any()))
                .thenReturn(new CartService.AddResult(snapshot(900L, 1), true));

        var response = controller.add(new CartItemAddRequest(ItemType.DISH, 101L, null, 1, List.of()));

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().id()).isEqualTo(900L);
        verify(cartService).add(CUSTOMER, ItemType.DISH, 101L, null, 1, List.of());
    }

    @Test
    void addReturns200WhenMergedIntoExistingRow() {
        actingAsCustomer();
        when(cartService.add(anyLong(), any(), any(), any(), anyInt(), any()))
                .thenReturn(new CartService.AddResult(snapshot(900L, 3), false));

        var response = controller.add(new CartItemAddRequest(ItemType.DISH, 101L, null, 2, List.of()));

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody().quantity()).isEqualTo(3);
    }

    @Test
    void clearUsesTokenSubject() {
        actingAsCustomer();

        controller.clear();

        verify(cartService).clear(CUSTOMER);
    }

    @Test
    void updateQuantityReturns200WithRow() {
        actingAsCustomer();
        when(cartService.updateQuantity(eq(CUSTOMER), eq(900L), eq(5)))
                .thenReturn(Optional.of(snapshot(900L, 5)));

        var response = controller.updateQuantity(900L, new CartItemQuantityRequest(5));

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody().quantity()).isEqualTo(5);
    }

    @Test
    void updateQuantityZeroReturns204() {
        actingAsCustomer();
        when(cartService.updateQuantity(CUSTOMER, 900L, 0)).thenReturn(Optional.empty());

        var response = controller.updateQuantity(900L, new CartItemQuantityRequest(0));

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NO_CONTENT);
        assertThat(response.getBody()).isNull();
    }
}
