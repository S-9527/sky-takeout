package com.sky.cart.domain;

/**
 * 顾客选中的某个口味维度,如 {@code (辣度, 微辣)}。
 *
 * <p>不加 Bean Validation 注解:那是 api 层的事,而 {@code jakarta.validation} 不允许被
 * domain 依赖(架构规则 L3)。合法性由 {@code CartService} 对照菜品的口味配置判定,
 * 并且能给出更准确的错误码({@code CART_FLAVOR_REQUIRED} / {@code CART_FLAVOR_INVALID})。
 */
public record FlavorChoice(String name, String option) {
}
