package com.sky.catalog.service;

/**
 * 商品的可售视图,**服务层的跨上下文契约**。
 *
 * <p>购物车/订单等上下文需要"这件商品现在能不能买、叫什么、多少钱、属于哪个分类",但不能引用
 * catalog 的实体({@code Dish}/{@code Setmeal})——架构规则 L4 要求跨上下文只能经由对方的
 * service 包。这个 record 就是那个"经由"的形状:它是 catalog 主动给出的稳定快照,
 * 不含任何 ORM 注解散落的字段。
 *
 * @param available        当前是否可下单(起售或起售套餐,且所属分类启用)
 * @param unavailableReason 不可下单时的原因文案;可下单时为 null
 */
public record PurchasableItemView(
        Long id,
        String name,
        String imageUrl,
        Long priceCents,
        Long categoryId,
        String categoryName,
        boolean available,
        String unavailableReason
) {
}
