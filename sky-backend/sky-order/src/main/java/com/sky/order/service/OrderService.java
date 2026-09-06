package com.sky.order.service;

import com.sky.order.dto.OrdersCancelDTO;
import com.sky.order.dto.OrdersConfirmDTO;
import com.sky.order.dto.OrdersPageQueryDTO;
import com.sky.order.dto.OrdersPaymentDTO;
import com.sky.order.dto.OrdersRejectionDTO;
import com.sky.order.dto.OrdersSubmitDTO;
import com.sky.order.dto.GoodsSalesDTO;
import com.sky.result.PageResult;
import com.sky.order.vo.OrderPaymentVO;
import com.sky.order.vo.OrderStatisticsVO;
import com.sky.order.vo.OrderSubmitVO;
import com.sky.order.vo.OrderVO;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

public interface OrderService {
    /**
     * 用户下单
     * @param ordersSubmitDTO
     * @return
     */
    OrderSubmitVO submitOrder(OrdersSubmitDTO ordersSubmitDTO);

    /**
     * 订单支付
     * @param ordersPaymentDTO
     * @return
     */
    OrderPaymentVO payment(OrdersPaymentDTO ordersPaymentDTO) throws Exception;

    /**
     * 支付成功，修改订单状态
     * @param outTradeNo
     */
    void paySuccess(String outTradeNo);

    /**
     * 用户端订单分页查询
     * @param page
     * @param pageSize
     * @param status
     * @return
     */
    PageResult pageQuery4User(int page, int pageSize, Integer status);

    /**
     * 查询订单详情
     * @param id
     * @return
     */
    OrderVO details(Long id);

    /**
     * 用户取消订单
     * @param id
     */
    void userCancelById(Long id) throws Exception;

    /**
     * 再来一单
     * @param id
     */
    void repetition(Long id);

    /**
     * 条件搜索订单
     * @param ordersPageQueryDTO
     * @return
     */
    PageResult conditionSearch(OrdersPageQueryDTO ordersPageQueryDTO);

    /**
     * 各个状态的订单数量统计
     * @return
     */
    OrderStatisticsVO statistics();

    /**
     * 接单
     *
     * @param ordersConfirmDTO
     */
    void confirm(OrdersConfirmDTO ordersConfirmDTO);

    /**
     * 拒单
     *
     * @param ordersRejectionDTO
     */
    void rejection(OrdersRejectionDTO ordersRejectionDTO) throws Exception;

    /**
     * 商家取消订单
     *
     * @param ordersCancelDTO
     */
    void cancel(OrdersCancelDTO ordersCancelDTO) throws Exception;

    /**
     * 派送订单
     *
     * @param id
     */
    void delivery(Long id);

    /**
     * 完成订单
     *
     * @param id
     */
    void complete(Long id);

    /**
     * 客户催单
     * @param id
     */
    void reminder(Long id);

    /**
     * 根据条件统计订单数量
     * @param map
     * @return
     */
    Integer countByMap(Map map);

    /**
     * 根据条件统计营业额
     * @param map
     * @return
     */
    Double sumByMap(Map map);

    /**
     * 查询销量排名Top10
     * @param begin
     * @param end
     * @return
     */
    List<GoodsSalesDTO> getSalesTop10(LocalDateTime begin, LocalDateTime end);
}
