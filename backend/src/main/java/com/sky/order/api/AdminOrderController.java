package com.sky.order.api;

import jakarta.validation.Valid;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;

import com.sky.common.domain.PageQuery;
import com.sky.common.domain.PageResponse;
import com.sky.common.domain.SortSpec;
import com.sky.order.api.dto.CancelOrderRequest;
import com.sky.order.api.dto.OrderDetailResponse;
import com.sky.order.api.dto.OrderItemResponse;
import com.sky.order.api.dto.OrderResponse;
import com.sky.order.api.dto.OrderStatusCountsResponse;
import com.sky.order.api.dto.RejectOrderRequest;
import com.sky.order.domain.Order;
import com.sky.order.domain.OrderStatus;
import com.sky.order.service.OrderCancellationService;
import com.sky.order.service.OrderService;

/** 管理端订单:查询 / 统计 / 接单 / 拒单 / 派送 / 完成 / 取消。前缀由 SecurityConfig 限制为 ADMIN / STAFF。 */
@RestController
@RequestMapping("/api/v1/admin/orders")
public class AdminOrderController {

    private final OrderService orderService;
    private final OrderCancellationService orderCancellationService;

    public AdminOrderController(OrderService orderService, OrderCancellationService orderCancellationService) {
        this.orderService = orderService;
        this.orderCancellationService = orderCancellationService;
    }

    @GetMapping
    public PageResponse<OrderResponse> page(
            @RequestParam(required = false) Integer page,
            @RequestParam(required = false) Integer pageSize,
            @RequestParam(required = false) String sort,
            @RequestParam(required = false) OrderStatus status,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate beginDate,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate endDate,
            @RequestParam(required = false) String orderNo,
            @RequestParam(required = false) String phone,
            @RequestParam(required = false) Long customerId) {
        SortSpec sortSpec = SortSpec.parse(sort, OrderService.SORT_WHITELIST, "placedAt", true);
        PageResponse<Order> result = orderService.pageForAdmin(status, beginDate, endDate, orderNo, phone,
                customerId, PageQuery.of(page, pageSize), sortSpec);
        return result.map(OrderResponse::from);
    }

    @GetMapping("/status-counts")
    public OrderStatusCountsResponse statusCounts() {
        return OrderStatusCountsResponse.from(orderService.statusCounts());
    }

    @GetMapping("/{id}")
    public OrderDetailResponse getById(@PathVariable Long id) {
        Order order = orderService.requireById(id);
        return OrderDetailResponse.from(order,
                orderService.itemsOf(id).stream().map(OrderItemResponse::from).toList());
    }

    @PostMapping("/{id}/acceptance")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void accept(@PathVariable Long id) {
        orderService.accept(id);
    }

    /** 拒单:已支付订单会先发起退款,退款受理失败则订单保持原状态(R6)。 */
    @PostMapping("/{id}/rejection")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void reject(@PathVariable Long id, @Valid @RequestBody RejectOrderRequest request) {
        orderCancellationService.reject(id, request.reason());
    }

    @PostMapping("/{id}/delivery")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void startDelivery(@PathVariable Long id) {
        orderService.startDelivery(id);
    }

    @PostMapping("/{id}/completion")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void complete(@PathVariable Long id) {
        orderService.complete(id);
    }

    /** 商家取消:DELIVERING 不可取消(状态机不允许),已支付必须先退款(R6)。 */
    @PostMapping("/{id}/cancellation")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void cancel(@PathVariable Long id, @Valid @RequestBody CancelOrderRequest request) {
        orderCancellationService.cancelByMerchant(id, request.reason());
    }
}
