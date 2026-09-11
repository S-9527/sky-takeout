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
import com.sky.order.api.dto.OrderPreviewRequest;
import com.sky.order.api.dto.OrderPreviewResponse;
import com.sky.order.api.dto.OrderRemindRequest;
import com.sky.order.api.dto.OrderResponse;
import com.sky.order.api.dto.OrderSubmitRequest;
import com.sky.order.api.dto.OrderSubmitResultResponse;
import com.sky.order.api.dto.ReorderResultResponse;
import com.sky.order.domain.Order;
import com.sky.order.domain.OrderStatus;
import com.sky.order.service.OrderService;
import com.sky.security.CurrentPrincipal;

/**
 * 顾客端订单。整个前缀由 SecurityConfig 限制为 CUSTOMER;顾客 id 一律取自令牌(R9),
 * 路径与请求体里没有可越权的输入。
 */
@RestController
@RequestMapping("/api/v1/customer/orders")
public class OrderController {

    private final OrderService orderService;

    public OrderController(OrderService orderService) {
        this.orderService = orderService;
    }

    /** 结算页试算:不落库、不清空购物车;打烊时返回 {@code shopOpen=false} 而不是报错。 */
    @PostMapping("/preview")
    public OrderPreviewResponse preview(@RequestBody OrderPreviewRequest request) {
        return OrderPreviewResponse.from(orderService.preview(
                CurrentPrincipal.requireCustomerId(), request == null ? null : request.addressId()));
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public OrderSubmitResultResponse submit(@Valid @RequestBody OrderSubmitRequest request) {
        return OrderSubmitResultResponse.from(orderService.submit(
                CurrentPrincipal.requireCustomerId(),
                request.addressId(), request.remark(), request.tablewareCount(),
                request.expectedTotalAmountCents()));
    }

    @GetMapping
    public PageResponse<OrderResponse> page(
            @RequestParam(required = false) Integer page,
            @RequestParam(required = false) Integer pageSize,
            @RequestParam(required = false) String sort,
            @RequestParam(required = false) OrderStatus status,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate placedAtFrom,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate placedAtTo) {
        SortSpec sortSpec = SortSpec.parse(sort, OrderService.SORT_WHITELIST, "placedAt", true);
        PageResponse<Order> result = orderService.page(CurrentPrincipal.requireCustomerId(), status,
                placedAtFrom, placedAtTo, PageQuery.of(page, pageSize), sortSpec);
        return result.map(OrderResponse::from);
    }

    @GetMapping("/{id}")
    public OrderDetailResponse getById(@PathVariable Long id) {
        Order order = orderService.requireOwned(CurrentPrincipal.requireCustomerId(), id);
        return OrderDetailResponse.from(order,
                orderService.itemsOf(id).stream().map(OrderItemResponse::from).toList());
    }

    @PostMapping("/{id}/cancellation")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void cancel(@PathVariable Long id, @RequestBody(required = false) CancelOrderRequest request) {
        orderService.cancelByCustomer(CurrentPrincipal.requireCustomerId(), id,
                request == null ? null : request.reason());
    }

    /** 再来一单:重新加购,不自动下单;下架/已删除的商品会出现在 {@code skippedItems}。 */
    @PostMapping("/{id}/reorder")
    public ReorderResultResponse reorder(@PathVariable Long id) {
        return ReorderResultResponse.from(
                orderService.reorder(CurrentPrincipal.requireCustomerId(), id));
    }

    @PostMapping("/{id}/reminders")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void remind(@PathVariable Long id, @RequestBody(required = false) OrderRemindRequest request) {
        orderService.remind(CurrentPrincipal.requireCustomerId(), id,
                request == null ? null : request.message());
    }
}
