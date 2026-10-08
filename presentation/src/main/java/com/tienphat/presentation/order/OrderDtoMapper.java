package com.tienphat.presentation.order;

import com.tienphat.application.auth.AuthorizationContext;
import com.tienphat.application.order.GetOrderStatusCommand;
import com.tienphat.application.order.OrderStatusResult;
import com.tienphat.application.reservation.ReserveTicketCommand;
import com.tienphat.presentation.order.dto.OrderResponse;
import com.tienphat.presentation.order.dto.ReserveOrderRequest;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

import java.util.UUID;

@Mapper(componentModel = "spring")
public interface OrderDtoMapper {

    @Mapping(target = "actor", source = "actor")
    ReserveTicketCommand toCommand(ReserveOrderRequest request, AuthorizationContext actor);

    OrderResponse toResponse(OrderStatusResult result);

    default GetOrderStatusCommand toStatusCommand(UUID orderId, AuthorizationContext actor) {
        return new GetOrderStatusCommand(orderId, actor);
    }
}
