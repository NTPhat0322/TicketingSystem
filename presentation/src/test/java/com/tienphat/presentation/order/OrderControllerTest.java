package com.tienphat.presentation.order;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.tienphat.application.auth.AuthorizationContext;
import com.tienphat.application.order.CancelOrderCommand;
import com.tienphat.application.order.GetOrderStatusCommand;
import com.tienphat.application.order.ListMyOrdersCommand;
import com.tienphat.application.order.OrderStatusResult;
import com.tienphat.application.order.OrderTrackingStatus;
import com.tienphat.application.reservation.ReserveTicketCommand;
import com.tienphat.application.reservation.ReserveTicketResult;
import com.tienphat.application.ticket.GetOrderTicketsCommand;
import com.tienphat.application.ticket.TicketResult;
import com.tienphat.application.usecase.UseCase;
import com.tienphat.domain.exception.ForbiddenOperationException;
import com.tienphat.domain.exception.InvalidOrderStateException;
import com.tienphat.domain.exception.OrderNotFoundException;
import com.tienphat.domain.exception.ReservationServiceUnavailableException;
import com.tienphat.domain.exception.TicketTypeSoldOutException;
import com.tienphat.domain.model.TicketStatus;
import com.tienphat.domain.model.UserRole;
import com.tienphat.domain.repository.PageRequest;
import com.tienphat.domain.repository.PageResult;
import com.tienphat.presentation.config.SecurityConfig;
import com.tienphat.presentation.ticket.TicketDtoMapperImpl;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(OrderController.class)
@Import({SecurityConfig.class, OrderDtoMapperImpl.class, TicketDtoMapperImpl.class})
class OrderControllerTest {

    private static final UUID USER_ID = UUID.fromString("0199c1b0-2d25-7a2c-8c44-7bb7b92e6d21");
    private static final UUID EVENT_ID = UUID.randomUUID();
    private static final UUID TICKET_TYPE_ID = UUID.randomUUID();
    private static final UUID ORDER_ID = UUID.fromString("0199c1b0-2d25-7a2c-8c44-7bb7b92e6d22");
    private static final Instant RESERVED_AT = Instant.parse("2026-09-28T08:00:00Z");
    private static final Instant EXPIRES_AT = RESERVED_AT.plusSeconds(300);

    @Autowired
    private MockMvc mockMvc;

    private final ObjectMapper objectMapper = new ObjectMapper().findAndRegisterModules();

    @MockitoBean
    private UseCase<ReserveTicketCommand, ReserveTicketResult> reserveTicketUseCase;

    @MockitoBean
    private UseCase<GetOrderStatusCommand, OrderStatusResult> getOrderStatusUseCase;

    @MockitoBean
    private UseCase<ListMyOrdersCommand, PageResult<OrderStatusResult>> listMyOrdersUseCase;

    @MockitoBean
    private UseCase<GetOrderTicketsCommand, List<TicketResult>> getOrderTicketsUseCase;

    @MockitoBean
    private UseCase<CancelOrderCommand, OrderStatusResult> cancelOrderUseCase;

    @Test
    void reserveReturns202AndCreatingSnapshot() throws Exception {
        when(reserveTicketUseCase.execute(any())).thenReturn(new ReserveTicketResult(
                ORDER_ID, USER_ID, OrderTrackingStatus.CREATING, EVENT_ID, TICKET_TYPE_ID,
                2, new BigDecimal("150000.00"), RESERVED_AT, EXPIRES_AT));

        mockMvc.perform(post("/api/v1/orders")
                        .with(jwtFor("CUSTOMER"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "eventId", EVENT_ID,
                                "ticketTypeId", TICKET_TYPE_ID,
                                "quantity", 2))))
                .andExpect(status().isAccepted())
                .andExpect(jsonPath("$.orderId").value(ORDER_ID.toString()))
                .andExpect(jsonPath("$.status").value("CREATING"))
                .andExpect(jsonPath("$.totalAmount").value(300000.0));

        var captor = org.mockito.ArgumentCaptor.forClass(ReserveTicketCommand.class);
        verify(reserveTicketUseCase).execute(captor.capture());
        assertThat(captor.getValue().actor()).isEqualTo(new AuthorizationContext(USER_ID, UserRole.CUSTOMER));
        assertThat(captor.getValue().quantity()).isEqualTo(2);
    }

    @Test
    void pollingReturnsPersistedStatus() throws Exception {
        when(getOrderStatusUseCase.execute(any())).thenReturn(new OrderStatusResult(
                ORDER_ID, OrderTrackingStatus.PENDING_PAYMENT, USER_ID, EVENT_ID, TICKET_TYPE_ID,
                2, new BigDecimal("150000.00"), new BigDecimal("300000.00"),
                RESERVED_AT, EXPIRES_AT, RESERVED_AT, RESERVED_AT,
                null, null, null));

        mockMvc.perform(get("/api/v1/orders/{orderId}", ORDER_ID).with(jwtFor("CUSTOMER")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("PENDING_PAYMENT"))
                .andExpect(jsonPath("$.orderId").value(ORDER_ID.toString()));

        verify(getOrderStatusUseCase).execute(any(GetOrderStatusCommand.class));
    }

    @Test
    void invalidQuantityReturns400WithoutCallingUseCase() throws Exception {
        mockMvc.perform(post("/api/v1/orders")
                        .with(jwtFor("CUSTOMER"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "eventId", EVENT_ID,
                                "ticketTypeId", TICKET_TYPE_ID,
                                "quantity", 0))))
                .andExpect(status().isBadRequest());

        verifyNoInteractions(reserveTicketUseCase);
    }

    @Test
    void soldOutIsMappedToConflict() throws Exception {
        when(reserveTicketUseCase.execute(any()))
                .thenThrow(new TicketTypeSoldOutException("sold out"));

        mockMvc.perform(post("/api/v1/orders")
                        .with(jwtFor("CUSTOMER"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "eventId", EVENT_ID,
                                "ticketTypeId", TICKET_TYPE_ID,
                                "quantity", 1))))
                .andExpect(status().isConflict());
    }

    @Test
    void redisUnavailableIsMappedToServiceUnavailable() throws Exception {
        when(reserveTicketUseCase.execute(any()))
                .thenThrow(new ReservationServiceUnavailableException("redis unavailable"));

        mockMvc.perform(post("/api/v1/orders")
                        .with(jwtFor("CUSTOMER"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "eventId", EVENT_ID,
                                "ticketTypeId", TICKET_TYPE_ID,
                                "quantity", 1))))
                .andExpect(status().isServiceUnavailable());
    }

    @Test
    void foreignOrderPollingIsMappedToForbidden() throws Exception {
        when(getOrderStatusUseCase.execute(any()))
                .thenThrow(new ForbiddenOperationException("foreign order"));

        mockMvc.perform(get("/api/v1/orders/{orderId}", ORDER_ID).with(jwtFor("CUSTOMER")))
                .andExpect(status().isForbidden());
    }

    @Test
    void unauthenticatedReservationIsRejected() throws Exception {
        mockMvc.perform(post("/api/v1/orders")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "eventId", EVENT_ID,
                                "ticketTypeId", TICKET_TYPE_ID,
                                "quantity", 1))))
                .andExpect(status().isUnauthorized());

        verifyNoInteractions(reserveTicketUseCase);
    }

    @Test
    void listMineReturnsPageEnvelopeForCaller() throws Exception {
        when(listMyOrdersUseCase.execute(any())).thenReturn(new PageResult<>(List.of(paidOrder()), 1, 5, 6));

        mockMvc.perform(get("/api/v1/orders").param("page", "1").param("size", "5").with(jwtFor("CUSTOMER")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].orderId").value(ORDER_ID.toString()))
                .andExpect(jsonPath("$.content[0].status").value("PAID"))
                .andExpect(jsonPath("$.page").value(1))
                .andExpect(jsonPath("$.size").value(5))
                .andExpect(jsonPath("$.totalElements").value(6))
                .andExpect(jsonPath("$.totalPages").value(2));

        var captor = org.mockito.ArgumentCaptor.forClass(ListMyOrdersCommand.class);
        verify(listMyOrdersUseCase).execute(captor.capture());
        assertThat(captor.getValue().actor()).isEqualTo(new AuthorizationContext(USER_ID, UserRole.CUSTOMER));
        assertThat(captor.getValue().pageRequest()).isEqualTo(new PageRequest(1, 5));
    }

    @Test
    void listMineRejectsOversizedPage() throws Exception {
        mockMvc.perform(get("/api/v1/orders").param("size", "101").with(jwtFor("CUSTOMER")))
                .andExpect(status().isBadRequest());

        verifyNoInteractions(listMyOrdersUseCase);
    }

    @Test
    void listMineRequiresAuthentication() throws Exception {
        mockMvc.perform(get("/api/v1/orders"))
                .andExpect(status().isUnauthorized());

        verifyNoInteractions(listMyOrdersUseCase);
    }

    @Test
    void ticketsReturnsIssuedTicketsForOrder() throws Exception {
        UUID ticketId = UUID.randomUUID();
        when(getOrderTicketsUseCase.execute(any())).thenReturn(List.of(new TicketResult(
                ticketId, "TKT-1", TicketStatus.ISSUED, RESERVED_AT, null, UUID.randomUUID(),
                TICKET_TYPE_ID, "VIP", EVENT_ID, "Rock Night", "Hall A", EXPIRES_AT, EXPIRES_AT.plusSeconds(7200))));

        mockMvc.perform(get("/api/v1/orders/{orderId}/tickets", ORDER_ID).with(jwtFor("CUSTOMER")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].ticketId").value(ticketId.toString()))
                .andExpect(jsonPath("$[0].ticketCode").value("TKT-1"))
                .andExpect(jsonPath("$[0].ticketTypeName").value("VIP"))
                .andExpect(jsonPath("$[0].eventName").value("Rock Night"));

        var captor = org.mockito.ArgumentCaptor.forClass(GetOrderTicketsCommand.class);
        verify(getOrderTicketsUseCase).execute(captor.capture());
        assertThat(captor.getValue().orderId()).isEqualTo(ORDER_ID);
    }

    @Test
    void ticketsOfForeignOrderIsMappedToForbidden() throws Exception {
        when(getOrderTicketsUseCase.execute(any())).thenThrow(new ForbiddenOperationException("foreign order"));

        mockMvc.perform(get("/api/v1/orders/{orderId}/tickets", ORDER_ID).with(jwtFor("CUSTOMER")))
                .andExpect(status().isForbidden());
    }

    @Test
    void ticketsOfUnknownOrderIsMappedToNotFound() throws Exception {
        when(getOrderTicketsUseCase.execute(any())).thenThrow(new OrderNotFoundException("missing"));

        mockMvc.perform(get("/api/v1/orders/{orderId}/tickets", ORDER_ID).with(jwtFor("CUSTOMER")))
                .andExpect(status().isNotFound());
    }

    @Test
    void cancelReturnsCancelledOrderForCaller() throws Exception {
        when(cancelOrderUseCase.execute(any())).thenReturn(new OrderStatusResult(
                ORDER_ID, OrderTrackingStatus.CANCELLED, USER_ID, EVENT_ID, TICKET_TYPE_ID,
                2, new BigDecimal("150000.00"), new BigDecimal("300000.00"),
                RESERVED_AT, EXPIRES_AT, RESERVED_AT, RESERVED_AT,
                null, null, null));

        mockMvc.perform(post("/api/v1/orders/{orderId}/cancel", ORDER_ID).with(jwtFor("CUSTOMER")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("CANCELLED"))
                .andExpect(jsonPath("$.orderId").value(ORDER_ID.toString()));

        var captor = org.mockito.ArgumentCaptor.forClass(CancelOrderCommand.class);
        verify(cancelOrderUseCase).execute(captor.capture());
        assertThat(captor.getValue().orderId()).isEqualTo(ORDER_ID);
        assertThat(captor.getValue().actor()).isEqualTo(new AuthorizationContext(USER_ID, UserRole.CUSTOMER));
    }

    @Test
    void cancelRequiresAuthentication() throws Exception {
        mockMvc.perform(post("/api/v1/orders/{orderId}/cancel", ORDER_ID))
                .andExpect(status().isUnauthorized());

        verifyNoInteractions(cancelOrderUseCase);
    }

    @Test
    void cancelOfForeignOrderIsMappedToForbidden() throws Exception {
        when(cancelOrderUseCase.execute(any())).thenThrow(new ForbiddenOperationException("foreign order"));

        mockMvc.perform(post("/api/v1/orders/{orderId}/cancel", ORDER_ID).with(jwtFor("CUSTOMER")))
                .andExpect(status().isForbidden());
    }

    @Test
    void cancelOfUnknownOrderIsMappedToNotFound() throws Exception {
        when(cancelOrderUseCase.execute(any())).thenThrow(new OrderNotFoundException("missing"));

        mockMvc.perform(post("/api/v1/orders/{orderId}/cancel", ORDER_ID).with(jwtFor("CUSTOMER")))
                .andExpect(status().isNotFound());
    }

    @Test
    void cancelOfNonPendingOrderIsMappedToConflict() throws Exception {
        when(cancelOrderUseCase.execute(any())).thenThrow(new InvalidOrderStateException("already paid"));

        mockMvc.perform(post("/api/v1/orders/{orderId}/cancel", ORDER_ID).with(jwtFor("CUSTOMER")))
                .andExpect(status().isConflict());
    }

    private static OrderStatusResult paidOrder() {
        return new OrderStatusResult(
                ORDER_ID, OrderTrackingStatus.PAID, USER_ID, EVENT_ID, TICKET_TYPE_ID,
                2, new BigDecimal("150000.00"), new BigDecimal("300000.00"),
                RESERVED_AT, EXPIRES_AT, RESERVED_AT, RESERVED_AT,
                null, null, null);
    }

    private static RequestPostProcessor jwtFor(String role) {
        return jwt()
                .jwt(jwt -> jwt.subject(USER_ID.toString()).claim("role", role))
                .authorities(new SimpleGrantedAuthority("ROLE_" + role));
    }
}
