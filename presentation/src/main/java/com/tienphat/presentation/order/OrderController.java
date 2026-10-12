package com.tienphat.presentation.order;

import com.tienphat.application.order.CancelOrderCommand;
import com.tienphat.application.order.GetOrderStatusCommand;
import com.tienphat.application.order.ListMyOrdersCommand;
import com.tienphat.application.order.OrderStatusResult;
import com.tienphat.application.reservation.ReserveTicketCommand;
import com.tienphat.application.reservation.ReserveTicketResult;
import com.tienphat.application.ticket.GetOrderTicketsCommand;
import com.tienphat.application.ticket.TicketResult;
import com.tienphat.application.usecase.UseCase;
import com.tienphat.domain.repository.PageRequest;
import com.tienphat.domain.repository.PageResult;
import com.tienphat.presentation.auth.JwtAuthorizationContext;
import com.tienphat.presentation.config.OpenApiConfig;
import com.tienphat.presentation.dto.PageResponse;
import com.tienphat.presentation.order.dto.OrderResponse;
import com.tienphat.presentation.order.dto.ReserveOrderRequest;
import com.tienphat.presentation.ticket.TicketDtoMapper;
import com.tienphat.presentation.ticket.dto.TicketResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

@RestController
@Validated
@RequestMapping("/api/v1/orders")
@SecurityRequirement(name = OpenApiConfig.BEARER_AUTH_SCHEME)
public class OrderController {

    private final UseCase<ReserveTicketCommand, ReserveTicketResult> reserveTicketUseCase;
    private final UseCase<GetOrderStatusCommand, OrderStatusResult> getOrderStatusUseCase;
    private final UseCase<ListMyOrdersCommand, PageResult<OrderStatusResult>> listMyOrdersUseCase;
    private final UseCase<GetOrderTicketsCommand, List<TicketResult>> getOrderTicketsUseCase;
    private final UseCase<CancelOrderCommand, OrderStatusResult> cancelOrderUseCase;
    private final OrderDtoMapper mapper;
    private final TicketDtoMapper ticketMapper;

    public OrderController(
            UseCase<ReserveTicketCommand, ReserveTicketResult> reserveTicketUseCase,
            UseCase<GetOrderStatusCommand, OrderStatusResult> getOrderStatusUseCase,
            UseCase<ListMyOrdersCommand, PageResult<OrderStatusResult>> listMyOrdersUseCase,
            UseCase<GetOrderTicketsCommand, List<TicketResult>> getOrderTicketsUseCase,
            UseCase<CancelOrderCommand, OrderStatusResult> cancelOrderUseCase,
            OrderDtoMapper mapper,
            TicketDtoMapper ticketMapper) {
        this.reserveTicketUseCase = reserveTicketUseCase;
        this.getOrderStatusUseCase = getOrderStatusUseCase;
        this.listMyOrdersUseCase = listMyOrdersUseCase;
        this.getOrderTicketsUseCase = getOrderTicketsUseCase;
        this.cancelOrderUseCase = cancelOrderUseCase;
        this.mapper = mapper;
        this.ticketMapper = ticketMapper;
    }

    @PostMapping
    @ResponseStatus(HttpStatus.ACCEPTED)
    public OrderResponse reserve(
            @Valid @RequestBody ReserveOrderRequest request,
            @AuthenticationPrincipal Jwt jwt) {
        ReserveTicketResult result = reserveTicketUseCase.execute(
                mapper.toCommand(request, JwtAuthorizationContext.from(jwt)));
        return mapper.toResponse(OrderStatusResult.fromReservation(result));
    }

    @GetMapping
    @Operation(summary = "List the caller's orders, newest first")
    public PageResponse<OrderResponse> listMine(
            @RequestParam(defaultValue = "0") @Min(0) int page,
            @RequestParam(defaultValue = "20") @Min(1) @Max(100) int size,
            @AuthenticationPrincipal Jwt jwt) {
        PageResult<OrderStatusResult> result = listMyOrdersUseCase.execute(
                new ListMyOrdersCommand(JwtAuthorizationContext.from(jwt), new PageRequest(page, size)));
        return mapper.toPageResponse(result);
    }

    @GetMapping("/{orderId}")
    public OrderResponse getStatus(
            @PathVariable UUID orderId,
            @AuthenticationPrincipal Jwt jwt) {
        OrderStatusResult result = getOrderStatusUseCase.execute(
                mapper.toStatusCommand(orderId, JwtAuthorizationContext.from(jwt)));
        return mapper.toResponse(result);
    }

    @GetMapping("/{orderId}/tickets")
    @Operation(summary = "List the tickets issued for a paid order")
    public List<TicketResponse> getTickets(
            @PathVariable UUID orderId,
            @AuthenticationPrincipal Jwt jwt) {
        List<TicketResult> result = getOrderTicketsUseCase.execute(
                new GetOrderTicketsCommand(orderId, JwtAuthorizationContext.from(jwt)));
        return ticketMapper.toResponses(result);
    }

    @PostMapping("/{orderId}/cancel")
    @Operation(summary = "Cancel a pending order and release its held tickets")
    public OrderResponse cancel(
            @PathVariable UUID orderId,
            @AuthenticationPrincipal Jwt jwt) {
        OrderStatusResult result = cancelOrderUseCase.execute(
                new CancelOrderCommand(orderId, JwtAuthorizationContext.from(jwt)));
        return mapper.toResponse(result);
    }
}
