package com.tienphat.presentation.order;

import com.tienphat.application.order.GetOrderStatusCommand;
import com.tienphat.application.order.OrderStatusResult;
import com.tienphat.application.reservation.ReserveTicketCommand;
import com.tienphat.application.reservation.ReserveTicketResult;
import com.tienphat.application.usecase.UseCase;
import com.tienphat.presentation.auth.JwtAuthorizationContext;
import com.tienphat.presentation.config.OpenApiConfig;
import com.tienphat.presentation.order.dto.OrderResponse;
import com.tienphat.presentation.order.dto.ReserveOrderRequest;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@RestController
@RequestMapping("/api/v1/orders")
@SecurityRequirement(name = OpenApiConfig.BEARER_AUTH_SCHEME)
public class OrderController {

    private final UseCase<ReserveTicketCommand, ReserveTicketResult> reserveTicketUseCase;
    private final UseCase<GetOrderStatusCommand, OrderStatusResult> getOrderStatusUseCase;
    private final OrderDtoMapper mapper;

    public OrderController(
            UseCase<ReserveTicketCommand, ReserveTicketResult> reserveTicketUseCase,
            UseCase<GetOrderStatusCommand, OrderStatusResult> getOrderStatusUseCase,
            OrderDtoMapper mapper) {
        this.reserveTicketUseCase = reserveTicketUseCase;
        this.getOrderStatusUseCase = getOrderStatusUseCase;
        this.mapper = mapper;
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

    @GetMapping("/{orderId}")
    public OrderResponse getStatus(
            @PathVariable UUID orderId,
            @AuthenticationPrincipal Jwt jwt) {
        OrderStatusResult result = getOrderStatusUseCase.execute(
                mapper.toStatusCommand(orderId, JwtAuthorizationContext.from(jwt)));
        return mapper.toResponse(result);
    }
}
