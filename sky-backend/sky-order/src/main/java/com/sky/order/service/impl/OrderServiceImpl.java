package com.sky.order.service.impl;

import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.JSONArray;
import com.alibaba.fastjson.JSONObject;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.sky.constant.MessageConstant;
import com.sky.utils.SecurityUtils;
import com.sky.order.dto.OrdersCancelDTO;
import com.sky.order.dto.OrdersConfirmDTO;
import com.sky.order.dto.OrdersPageQueryDTO;
import com.sky.order.dto.OrdersPaymentDTO;
import com.sky.order.dto.OrdersRejectionDTO;
import com.sky.order.dto.OrdersSubmitDTO;
import com.sky.order.dto.GoodsSalesDTO;
import com.sky.order.domain.OrderStateMachine;
import com.sky.order.entity.OrderDetail;
import com.sky.order.entity.Orders;
import com.sky.order.enumeration.OrderStatus;
import com.sky.order.enumeration.OrderPayStatus;
import com.sky.order.event.OrderReminderEvent;
import com.sky.user.entity.AddressBook;
import com.sky.user.entity.ShoppingCart;
import com.sky.user.entity.User;
import com.sky.user.exception.AddressBookBusinessException;
import com.sky.order.exception.OrderBusinessException;
import com.sky.user.exception.ShoppingCartBusinessException;
import com.sky.user.service.AddressBookService;
import com.sky.user.service.ShoppingCartService;
import com.sky.user.service.UserService;
import com.sky.order.mapper.OrderDetailMapper;
import com.sky.order.mapper.OrderMapper;
import com.sky.result.PageResult;
import com.sky.result.ResultCode;
import com.sky.order.service.OrderService;
import com.sky.pay.PaymentGateway;
import com.sky.order.vo.OrderPaymentVO;
import com.sky.order.vo.OrderStatisticsVO;
import com.sky.order.vo.OrderSubmitVO;
import com.sky.order.vo.OrderVO;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.BeanUtils;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.http.HttpStatusCode;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.CollectionUtils;
import org.springframework.web.client.RestClient;
import org.springframework.web.util.UriComponentsBuilder;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;

@Service
@Slf4j
@RequiredArgsConstructor
public class OrderServiceImpl implements OrderService {

    private final OrderMapper orderMapper;
    private final OrderDetailMapper orderDetailMapper;
    private final AddressBookService addressBookService;
    private final ShoppingCartService shoppingCartService;
    private final UserService userService;
    private final PaymentGateway paymentGateway;
    private final OrderStateMachine orderStateMachine;
    private final ApplicationEventPublisher eventPublisher;
    private final RestClient.Builder restClientBuilder;

    /**
     * 用户下单
     * @param ordersSubmitDTO
     * @return
     */
    @Transactional
    public OrderSubmitVO submitOrder(OrdersSubmitDTO ordersSubmitDTO) {

        //1. 处理各种业务异常（地址簿为空、购物车数据为空）
        AddressBook addressBook = addressBookService.getById(ordersSubmitDTO.getAddressBookId());
        if(addressBook == null){
            //抛出业务异常
            throw new AddressBookBusinessException(MessageConstant.ADDRESS_BOOK_IS_NULL);
        }

        //检查用户的收货地址是否超出配送范围
        //checkOutOfRange(addressBook.getCityName() + addressBook.getDistrictName() + addressBook.getDetail());

        //查询当前用户的购物车数据
        Long userId = SecurityUtils.getCurrentUserId();

        ShoppingCart shoppingCart = new ShoppingCart();
        shoppingCart.setUserId(userId);
        List<ShoppingCart> shoppingCartList = shoppingCartService.list(shoppingCart);

        if(shoppingCartList == null || shoppingCartList.size() == 0){
            //抛出业务异常
            throw new ShoppingCartBusinessException(MessageConstant.SHOPPING_CART_IS_NULL);
        }

        //2. 向订单表插入1条数据
        Orders orders = new Orders();
        BeanUtils.copyProperties(ordersSubmitDTO, orders);
        orders.setOrderTime(LocalDateTime.now());
        orders.setPayStatus(OrderPayStatus.UN_PAID.getCode());
        orders.setStatus(OrderStatus.PENDING_PAYMENT.getCode());
        orders.setNumber(String.valueOf(System.currentTimeMillis()));
        orders.setAddress(addressBook.getDetail());
        orders.setPhone(addressBook.getPhone());
        orders.setConsignee(addressBook.getConsignee());
        orders.setUserId(userId);

        orderMapper.insert(orders);

        List<OrderDetail> orderDetailList = new ArrayList<>();
        //3. 向订单明细表插入n条数据
        for (ShoppingCart cart : shoppingCartList) {
            OrderDetail orderDetail = new OrderDetail();//订单明细
            BeanUtils.copyProperties(cart, orderDetail);
            orderDetail.setOrderId(orders.getId());//设置当前订单明细关联的订单id
            orderDetailList.add(orderDetail);
        }

        orderDetailMapper.insertBatch(orderDetailList);

        //4. 清空当前用户的购物车数据
        shoppingCartService.deleteByUserId(userId);

        //5. 封装VO返回结果
        OrderSubmitVO orderSubmitVO = OrderSubmitVO.builder()
                .id(orders.getId())
                .orderTime(orders.getOrderTime())
                .orderNumber(orders.getNumber())
                .orderAmount(orders.getAmount())
                .build();

        return orderSubmitVO;
    }

    @Value("${sky.shop.address}")
    private String shopAddress;

    @Value("${sky.baidu.ak}")
    private String ak;

    /**
     * 检查客户的收货地址是否超出配送范围
     * @param address
     */
    private void checkOutOfRange(String address) {
        Map<String, String> params = new HashMap<>();
        params.put("output", "json");
        params.put("ak", ak);

        //获取店铺的经纬度坐标
        params.put("address", shopAddress);
        String shopCoordinate = getMapApi("https://api.map.baidu.com/geocoding/v3", params, "店铺地址解析失败");

        JSONObject jsonObject = JSON.parseObject(shopCoordinate);
        if(!jsonObject.getString("status").equals("0")){
            throw new OrderBusinessException("店铺地址解析失败");
        }

        //数据解析
        JSONObject location = jsonObject.getJSONObject("result").getJSONObject("location");
        String lat = location.getString("lat");
        String lng = location.getString("lng");
        //店铺经纬度坐标
        String shopLngLat = lat + "," + lng;

        params.put("address", address);
        //获取用户收货地址的经纬度坐标
        String userCoordinate = getMapApi("https://api.map.baidu.com/geocoding/v3", params, "收货地址解析失败");

        jsonObject = JSON.parseObject(userCoordinate);
        if(!jsonObject.getString("status").equals("0")){
            throw new OrderBusinessException("收货地址解析失败");
        }

        //数据解析
        location = jsonObject.getJSONObject("result").getJSONObject("location");
        lat = location.getString("lat");
        lng = location.getString("lng");
        //用户收货地址经纬度坐标
        String userLngLat = lat + "," + lng;

        params.put("origin", shopLngLat);
        params.put("destination", userLngLat);
        params.put("steps_info", "0");

        //路线规划
        String json = getMapApi("https://api.map.baidu.com/directionlite/v1/driving", params, "配送路线规划失败");

        jsonObject = JSON.parseObject(json);
        if(!jsonObject.getString("status").equals("0")){
            throw new OrderBusinessException("配送路线规划失败");
        }

        //数据解析
        JSONObject result = jsonObject.getJSONObject("result");
        JSONArray jsonArray = (JSONArray) result.get("routes");
        Integer distance = (Integer) ((JSONObject) jsonArray.get(0)).get("distance");

        if(distance > 5000){
            //配送距离超过5000米
            throw new OrderBusinessException("超出配送范围");
        }
    }

    /**
     * 调用百度地图接口，非 2xx 或解析失败时抛出对应业务异常
     */
    private String getMapApi(String url, Map<String, String> params, String errorMsg) {
        UriComponentsBuilder builder = UriComponentsBuilder.fromUriString(url);
        params.forEach((key, value) -> builder.queryParam(key, value));
        return restClientBuilder.build().get()
                .uri(builder.build().toUri())
                .retrieve()
                .onStatus(HttpStatusCode::isError,
                        (request, response) -> { throw new OrderBusinessException(errorMsg); })
                .body(String.class);
    }

    /**
     * 订单支付
     *
     * @param ordersPaymentDTO
     * @return
     */
    @Transactional
    public OrderPaymentVO payment(OrdersPaymentDTO ordersPaymentDTO) throws Exception {
        // 当前登录用户id
        Long userId = SecurityUtils.getCurrentUserId();
        User user = userService.getById(userId);

        //调用支付网关生成预支付交易单
        JSONObject jsonObject = paymentGateway.pay(
                ordersPaymentDTO.getOrderNumber(), //商户订单号
                new BigDecimal(0.01), //支付金额，单位 元
                "苍穹外卖订单", //商品描述
                user.getOpenid() //微信用户的openid
        );

        if (jsonObject.getString("code") != null && jsonObject.getString("code").equals("ORDERPAID")) {
            throw new OrderBusinessException(ResultCode.CONFLICT.getCode(), "该订单已支付");
        }

        OrderPaymentVO vo = jsonObject.toJavaObject(OrderPaymentVO.class);
        vo.setPackageStr(jsonObject.getString("package"));

        // Mock 支付网关：本地开发环境直接确认支付成功
        if (paymentGateway.autoConfirmOnPay()) {
            paySuccess(ordersPaymentDTO.getOrderNumber());
        }

        return vo;
    }

    /**
     * 支付成功，修改订单状态
     *
     * @param outTradeNo
     */
    @Transactional
    public void paySuccess(String outTradeNo) {
        // 当前登录用户id
        Long userId = SecurityUtils.getCurrentUserId();

        // 根据订单号查询当前用户的订单
        Orders ordersDB = orderMapper.selectOne(new LambdaQueryWrapper<Orders>()
                .eq(Orders::getNumber, outTradeNo)
                .eq(Orders::getUserId, userId));

        if (ordersDB == null) {
            throw new OrderBusinessException(MessageConstant.ORDER_NOT_FOUND);
        }

        // 状态机流转：待付款 → 待接单，同时更新支付状态和结账时间
        // 状态变更事件由 OrderEventListener 负责推送来单提醒
        orderStateMachine.transition(ordersDB, OrderStatus.TO_BE_CONFIRMED, o -> {
            o.setPayStatus(OrderPayStatus.PAID.getCode());
            o.setCheckoutTime(LocalDateTime.now());
        }, "PAYED");
    }

    /**
     * 用户端订单分页查询
     *
     * @param pageNum
     * @param pageSize
     * @param status
     * @return
     */
    public PageResult<OrderVO> pageQuery4User(int pageNum, int pageSize, Integer status) {
        OrdersPageQueryDTO ordersPageQueryDTO = new OrdersPageQueryDTO();
        ordersPageQueryDTO.setUserId(SecurityUtils.getCurrentUserId());
        ordersPageQueryDTO.setStatus(status);

        // 分页条件查询
        IPage<Orders> page = orderMapper.pageQuery(
                new Page<>(pageNum, pageSize), ordersPageQueryDTO);

        List<OrderVO> list = new ArrayList<>();

        // 查询出订单明细，并封装入OrderVO进行响应
        if (page != null && page.getTotal() > 0) {
            for (Orders orders : page.getRecords()) {
                Long orderId = orders.getId();// 订单id

                // 查询订单明细
                List<OrderDetail> orderDetails = orderDetailMapper.selectList(
                        new LambdaQueryWrapper<OrderDetail>().eq(OrderDetail::getOrderId, orderId));

                OrderVO orderVO = new OrderVO();
                BeanUtils.copyProperties(orders, orderVO);
                orderVO.setOrderDetailList(orderDetails);

                list.add(orderVO);
            }
        }
        return new PageResult<>(page.getTotal(), list);
    }

    /**
     * 查询订单详情
     *
     * @param id
     * @return
     */
    public OrderVO details(Long id) {
        // 根据id查询订单
        Orders orders = orderMapper.selectById(id);

        // 查询该订单对应的菜品/套餐明细
        List<OrderDetail> orderDetailList = orderDetailMapper.selectList(
                new LambdaQueryWrapper<OrderDetail>().eq(OrderDetail::getOrderId, orders.getId()));

        // 将该订单及其详情封装到OrderVO并返回
        OrderVO orderVO = new OrderVO();
        BeanUtils.copyProperties(orders, orderVO);
        orderVO.setOrderDetailList(orderDetailList);

        return orderVO;
    }

    /**
     * 用户取消订单
     *
     * @param id
     */
    @Transactional
    public void userCancelById(Long id) throws Exception {
        // 根据id查询订单
        Orders ordersDB = orderMapper.selectById(id);

        // 校验订单是否存在
        if (ordersDB == null) {
            throw new OrderBusinessException(MessageConstant.ORDER_NOT_FOUND);
        }

        OrderStatus from = OrderStatus.fromCode(ordersDB.getStatus());
        if (from != OrderStatus.PENDING_PAYMENT && from != OrderStatus.TO_BE_CONFIRMED) {
            throw new OrderBusinessException(ResultCode.CONFLICT.getCode(), MessageConstant.ORDER_STATUS_ERROR);
        }

        // 订单处于待接单状态下取消，需要进行退款
        if (from == OrderStatus.TO_BE_CONFIRMED) {
            paymentGateway.refund(
                    ordersDB.getNumber(),
                    ordersDB.getNumber(),
                    new BigDecimal(0.01),
                    new BigDecimal(0.01));
            ordersDB.setPayStatus(OrderPayStatus.REFUND.getCode());
        }

        orderStateMachine.transition(ordersDB, OrderStatus.CANCELLED, o -> {
            o.setCancelReason("用户取消");
            o.setCancelTime(LocalDateTime.now());
        });
    }

    /**
     * 再来一单
     *
     * @param id
     */
    public void repetition(Long id) {
        // 查询当前用户id
        Long userId = SecurityUtils.getCurrentUserId();

        // 根据订单id查询当前订单详情
        List<OrderDetail> orderDetailList = orderDetailMapper.selectList(
                new LambdaQueryWrapper<OrderDetail>().eq(OrderDetail::getOrderId, id));

        // 将订单详情对象转换为购物车对象
        List<ShoppingCart> shoppingCartList = orderDetailList.stream().map(x -> {
            ShoppingCart shoppingCart = new ShoppingCart();

            // 将原订单详情里面的菜品信息重新复制到购物车对象中
            BeanUtils.copyProperties(x, shoppingCart, "id");
            shoppingCart.setUserId(userId);
            shoppingCart.setCreateTime(LocalDateTime.now());

            return shoppingCart;
        }).collect(Collectors.toList());

        // 将购物车对象批量添加到数据库
        shoppingCartService.insertBatch(shoppingCartList);
    }

    /**
     * 订单搜索
     *
     * @param ordersPageQueryDTO
     * @return
     */
    public PageResult<OrderVO> conditionSearch(OrdersPageQueryDTO ordersPageQueryDTO) {
        IPage<Orders> page = orderMapper.pageQuery(
                new Page<>(ordersPageQueryDTO.getPage(), ordersPageQueryDTO.getPageSize()),
                ordersPageQueryDTO);

        // 部分订单状态，需要额外返回订单菜品信息，将Orders转化为OrderVO
        List<OrderVO> orderVOList = getOrderVOList(page.getRecords());

        return new PageResult<>(page.getTotal(), orderVOList);
    }

    private List<OrderVO> getOrderVOList(List<Orders> ordersList) {
        // 需要返回订单菜品信息，自定义OrderVO响应结果
        List<OrderVO> orderVOList = new ArrayList<>();

        if (!CollectionUtils.isEmpty(ordersList)) {
            for (Orders orders : ordersList) {
                // 将共同字段复制到OrderVO
                OrderVO orderVO = new OrderVO();
                BeanUtils.copyProperties(orders, orderVO);
                String orderDishes = getOrderDishesStr(orders);

                // 将订单菜品信息封装到orderVO中，并添加到orderVOList
                orderVO.setOrderDishes(orderDishes);
                orderVOList.add(orderVO);
            }
        }
        return orderVOList;
    }

    /**
     * 根据订单id获取菜品信息字符串
     *
     * @param orders
     * @return
     */
    private String getOrderDishesStr(Orders orders) {
        // 查询订单菜品详情信息（订单中的菜品和数量）
        List<OrderDetail> orderDetailList = orderDetailMapper.selectList(
                new LambdaQueryWrapper<OrderDetail>().eq(OrderDetail::getOrderId, orders.getId()));

        // 将每一条订单菜品信息拼接为字符串（格式：宫保鸡丁*3；）
        List<String> orderDishList = orderDetailList.stream().map(x -> {
            String orderDish = x.getName() + "*" + x.getNumber() + ";";
            return orderDish;
        }).collect(Collectors.toList());

        // 将该订单对应的所有菜品信息拼接在一起
        return String.join("", orderDishList);
    }

    /**
     * 各个状态的订单数量统计
     *
     * @return
     */
    public OrderStatisticsVO statistics() {
        // 根据状态，分别查询出待接单、待派送、派送中的订单数量
        Long toBeConfirmed = orderMapper.selectCount(
                new LambdaQueryWrapper<Orders>().eq(Orders::getStatus, OrderStatus.TO_BE_CONFIRMED.getCode()));
        Long confirmed = orderMapper.selectCount(
                new LambdaQueryWrapper<Orders>().eq(Orders::getStatus, OrderStatus.CONFIRMED.getCode()));
        Long deliveryInProgress = orderMapper.selectCount(
                new LambdaQueryWrapper<Orders>().eq(Orders::getStatus, OrderStatus.DELIVERY_IN_PROGRESS.getCode()));

        // 将查询出的数据封装到orderStatisticsVO中响应
        OrderStatisticsVO orderStatisticsVO = new OrderStatisticsVO();
        orderStatisticsVO.setToBeConfirmed(toBeConfirmed.intValue());
        orderStatisticsVO.setConfirmed(confirmed.intValue());
        orderStatisticsVO.setDeliveryInProgress(deliveryInProgress.intValue());
        return orderStatisticsVO;
    }

    /**
     * 接单
     *
     * @param ordersConfirmDTO
     */
    @Transactional
    public void confirm(OrdersConfirmDTO ordersConfirmDTO) {
        Orders ordersDB = orderMapper.selectById(ordersConfirmDTO.getId());
        orderStateMachine.transition(ordersDB, OrderStatus.CONFIRMED, null);
    }

    /**
     * 拒单
     *
     * @param ordersRejectionDTO
     */
    @Transactional
    public void rejection(OrdersRejectionDTO ordersRejectionDTO) throws Exception {
        Orders ordersDB = orderMapper.selectById(ordersRejectionDTO.getId());

        if (ordersDB == null) {
            throw new OrderBusinessException(ResultCode.NOT_FOUND.getCode(), MessageConstant.ORDER_NOT_FOUND);
        }

        if (!OrderStatus.TO_BE_CONFIRMED.getCode().equals(ordersDB.getStatus())) {
            throw new OrderBusinessException(ResultCode.CONFLICT.getCode(), MessageConstant.ORDER_STATUS_ERROR);
        }

        // 已支付则退款
        if (OrderPayStatus.PAID.getCode().equals(ordersDB.getPayStatus())) {
            String refund = paymentGateway.refund(
                    ordersDB.getNumber(),
                    ordersDB.getNumber(),
                    new BigDecimal(0.01),
                    new BigDecimal(0.01));
            log.info("申请退款：{}", refund);
        }

        orderStateMachine.transition(ordersDB, OrderStatus.CANCELLED, o -> {
            o.setRejectionReason(ordersRejectionDTO.getRejectionReason());
            o.setCancelTime(LocalDateTime.now());
        });
    }

    /**
     * 取消订单
     *
     * @param ordersCancelDTO
     */
    @Transactional
    public void cancel(OrdersCancelDTO ordersCancelDTO) throws Exception {
        Orders ordersDB = orderMapper.selectById(ordersCancelDTO.getId());

        if (ordersDB == null) {
            throw new OrderBusinessException(ResultCode.NOT_FOUND.getCode(), MessageConstant.ORDER_NOT_FOUND);
        }

        // 先校验当前状态是否允许取消，避免对不可取消订单误触发退款
        OrderStatus from = OrderStatus.fromCode(ordersDB.getStatus());
        if (!orderStateMachine.canTransition(from, OrderStatus.CANCELLED)) {
            throw new OrderBusinessException(ResultCode.CONFLICT.getCode(), MessageConstant.ORDER_STATUS_ERROR);
        }

        // 已支付则退款
        if (OrderPayStatus.PAID.getCode().equals(ordersDB.getPayStatus())) {
            String refund = paymentGateway.refund(
                    ordersDB.getNumber(),
                    ordersDB.getNumber(),
                    new BigDecimal(0.01),
                    new BigDecimal(0.01));
            log.info("申请退款：{}", refund);
            ordersDB.setPayStatus(OrderPayStatus.REFUND.getCode());
        }

        orderStateMachine.transition(ordersDB, OrderStatus.CANCELLED, o -> {
            o.setCancelReason(ordersCancelDTO.getCancelReason());
            o.setCancelTime(LocalDateTime.now());
        });
    }

    /**
     * 派送订单
     *
     * @param id
     */
    @Transactional
    public void delivery(Long id) {
        Orders ordersDB = orderMapper.selectById(id);

        if (ordersDB == null) {
            throw new OrderBusinessException(ResultCode.NOT_FOUND.getCode(), MessageConstant.ORDER_NOT_FOUND);
        }

        if (!OrderStatus.CONFIRMED.getCode().equals(ordersDB.getStatus())) {
            throw new OrderBusinessException(ResultCode.CONFLICT.getCode(), MessageConstant.ORDER_STATUS_ERROR);
        }

        orderStateMachine.transition(ordersDB, OrderStatus.DELIVERY_IN_PROGRESS, null);
    }

    /**
     * 完成订单
     *
     * @param id
     */
    @Transactional
    public void complete(Long id) {
        Orders ordersDB = orderMapper.selectById(id);

        if (ordersDB == null) {
            throw new OrderBusinessException(ResultCode.NOT_FOUND.getCode(), MessageConstant.ORDER_NOT_FOUND);
        }

        if (!OrderStatus.DELIVERY_IN_PROGRESS.getCode().equals(ordersDB.getStatus())) {
            throw new OrderBusinessException(ResultCode.CONFLICT.getCode(), MessageConstant.ORDER_STATUS_ERROR);
        }

        orderStateMachine.transition(ordersDB, OrderStatus.COMPLETED, o -> o.setDeliveryTime(LocalDateTime.now()));
    }

    /**
     * 客户催单
     * @param id
     */
    @Transactional
    public void reminder(Long id) {
        // 根据id查询订单
        Orders ordersDB = orderMapper.selectById(id);

        // 校验订单是否存在
        if (ordersDB == null) {
            throw new OrderBusinessException(ResultCode.NOT_FOUND.getCode(), MessageConstant.ORDER_NOT_FOUND);
        }

        // 客户催单事件由 OrderEventListener 负责推送
        eventPublisher.publishEvent(new OrderReminderEvent(id, ordersDB.getNumber()));
    }

    /**
     * 根据条件统计订单数量
     * @param map
     * @return
     */
    public Integer countByMap(Map map) {
        return orderMapper.countByMap(map);
    }

    /**
     * 根据条件统计营业额
     * @param map
     * @return
     */
    public Double sumByMap(Map map) {
        return orderMapper.sumByMap(map);
    }

    /**
     * 查询销量排名Top10
     * @param begin
     * @param end
     * @return
     */
    public List<GoodsSalesDTO> getSalesTop10(LocalDateTime begin, LocalDateTime end) {
        return orderMapper.getSalesTop10(begin, end);
    }
}
