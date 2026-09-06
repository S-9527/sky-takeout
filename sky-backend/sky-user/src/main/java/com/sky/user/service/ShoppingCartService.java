package com.sky.user.service;

import com.sky.user.dto.ShoppingCartDTO;
import com.sky.user.entity.ShoppingCart;

import java.util.List;

public interface ShoppingCartService {

    /**
     * 添加购物车
     * @param shoppingCartDTO
     */
    void addShoppingCart(ShoppingCartDTO shoppingCartDTO);

    /**
     * 查看购物车
     * @return
     */
    List<ShoppingCart> showShoppingCart();

    /**
     * 清空购物车
     */
    void cleanShoppingCart();

    /**
     * 删除购物车中一个商品
     * @param shoppingCartDTO
     */
    void subShoppingCart(ShoppingCartDTO shoppingCartDTO);

    /**
     * 根据条件查询购物车
     * @param shoppingCart
     * @return
     */
    List<ShoppingCart> list(ShoppingCart shoppingCart);

    /**
     * 批量插入购物车
     * @param shoppingCartList
     */
    void insertBatch(List<ShoppingCart> shoppingCartList);

    /**
     * 根据用户id删除购物车数据
     * @param userId
     */
    void deleteByUserId(Long userId);
}
