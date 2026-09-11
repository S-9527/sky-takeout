package com.sky.cart.api;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import com.sky.cart.api.dto.CartItemAddRequest;
import com.sky.cart.api.dto.CartItemQuantityRequest;
import com.sky.cart.api.dto.CartItemViewResponse;
import com.sky.cart.api.dto.CartViewResponse;
import com.sky.cart.service.CartService;
import com.sky.security.CurrentPrincipal;

/**
 * 顾客端购物车。整个前缀由 SecurityConfig 限制为 CUSTOMER;
 * 顾客 id 一律取自令牌,路径与请求体里没有可越权的输入(R9)。
 */
@RestController
@RequestMapping("/api/v1/customer/cart/items")
public class CartController {

    private final CartService cartService;

    public CartController(CartService cartService) {
        this.cartService = cartService;
    }

    @GetMapping
    public CartViewResponse list() {
        return CartViewResponse.from(cartService.summary(CurrentPrincipal.requireCustomerId()));
    }

    /** 新加一行返回 201;合并到已有行返回 200 并带上合并后的行。 */
    @PostMapping
    public ResponseEntity<CartItemViewResponse> add(@RequestBody CartItemAddRequest request) {
        CartService.AddResult result = cartService.add(
                CurrentPrincipal.requireCustomerId(),
                request.itemType(), request.dishId(), request.setmealId(),
                request.quantity(), request.flavorChoice());
        return ResponseEntity
                .status(result.created() ? HttpStatus.CREATED : HttpStatus.OK)
                .body(CartItemViewResponse.from(result.item()));
    }

    /** 清空购物车,幂等。 */
    @DeleteMapping
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void clear() {
        cartService.clear(CurrentPrincipal.requireCustomerId());
    }

    /** 覆盖式改量:数量为 0 时删除该行并返回 204。 */
    @PutMapping("/{id}/quantity")
    public ResponseEntity<CartItemViewResponse> updateQuantity(@PathVariable Long id,
                                                               @RequestBody CartItemQuantityRequest request) {
        return cartService.updateQuantity(CurrentPrincipal.requireCustomerId(), id, request.quantity())
                .map(item -> ResponseEntity.ok(CartItemViewResponse.from(item)))
                .orElseGet(() -> ResponseEntity.noContent().build());
    }
}
