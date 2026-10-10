package com.tienphat.presentation.config;

import com.tienphat.application.auth.LoginCommand;
import com.tienphat.application.auth.LoginResult;
import com.tienphat.application.auth.LoginUseCase;
import com.tienphat.application.auth.LogoutUseCase;
import com.tienphat.application.auth.RefreshTokenStore;
import com.tienphat.application.auth.RefreshUseCase;
import com.tienphat.application.event.CreateEventCommand;
import com.tienphat.application.event.CreateEventUseCase;
import com.tienphat.application.event.DeactivateEventCommand;
import com.tienphat.application.event.DeactivateEventUseCase;
import com.tienphat.application.event.EventMapper;
import com.tienphat.application.event.EventResult;
import com.tienphat.application.event.PublishEventCommand;
import com.tienphat.application.event.PublishEventUseCase;
import com.tienphat.application.event.GetEventUseCase;
import com.tienphat.application.event.ListEventsUseCase;
import com.tienphat.application.event.StartSaleCommand;
import com.tienphat.application.event.StartSaleUseCase;
import com.tienphat.application.event.UpdateEventCommand;
import com.tienphat.application.event.UpdateEventUseCase;
import com.tienphat.application.order.CreateOrderFromReservationCommand;
import com.tienphat.application.order.CreateOrderFromReservationUseCase;
import com.tienphat.application.order.ExpireOrderTransaction;
import com.tienphat.application.order.ExpireOrderUseCase;
import com.tienphat.application.order.GetOrderStatusCommand;
import com.tienphat.application.order.GetOrderStatusUseCase;
import com.tienphat.application.order.OrderStatusResult;
import com.tienphat.application.payment.ConfirmPaymentUseCase;
import com.tienphat.application.payment.InitiatePaymentUseCase;
import com.tienphat.application.payment.PaymentConfirmationTransaction;
import com.tienphat.application.payment.PaymentGatewayPort;
import com.tienphat.application.payment.TicketCodeGenerator;
import com.tienphat.application.payment.UuidTicketCodeGenerator;
import com.tienphat.application.reservation.ReserveTicketCommand;
import com.tienphat.application.reservation.ReserveTicketResult;
import com.tienphat.application.reservation.ReserveTicketUseCase;
import com.tienphat.application.reservation.ReservationRelayPort;
import com.tienphat.application.reservation.RunReservationRelayUseCase;
import com.tienphat.application.tickettype.CreateTicketTypeCommand;
import com.tienphat.application.tickettype.CreateTicketTypeUseCase;
import com.tienphat.application.tickettype.DeactivateTicketTypeCommand;
import com.tienphat.application.tickettype.DeactivateTicketTypeUseCase;
import com.tienphat.application.tickettype.GetTicketTypeUseCase;
import com.tienphat.application.tickettype.ListTicketTypesByEventUseCase;
import com.tienphat.application.tickettype.TicketTypeMapper;
import com.tienphat.application.tickettype.TicketTypeResult;
import com.tienphat.application.tickettype.UpdateTicketTypeCommand;
import com.tienphat.application.tickettype.UpdateTicketTypeUseCase;
import com.tienphat.application.user.ChangeUserRoleCommand;
import com.tienphat.application.user.ChangeUserRoleUseCase;
import com.tienphat.application.user.GetUserUseCase;
import com.tienphat.application.user.RegisterUserCommand;
import com.tienphat.application.user.RegisterUserUseCase;
import com.tienphat.application.user.UserMapper;
import com.tienphat.application.user.UserResult;
import com.tienphat.application.usecase.UseCase;
import com.tienphat.domain.repository.EventRepository;
import com.tienphat.domain.repository.OrderRepository;
import com.tienphat.domain.repository.OutboxEventRepository;
import com.tienphat.domain.repository.PaymentRepository;
import com.tienphat.domain.repository.PageRequest;
import com.tienphat.domain.repository.PageResult;
import com.tienphat.domain.repository.TicketRepository;
import com.tienphat.domain.repository.TicketTypeRepository;
import com.tienphat.domain.repository.UserRepository;
import com.tienphat.domain.port.ReservationIntentStatusPort;
import com.tienphat.domain.port.ReservationIntentStore;
import com.tienphat.domain.port.StockCachePort;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.util.List;
import java.util.UUID;

@Configuration
public class UseCaseConfig {

    @Bean
    public UseCase<RegisterUserCommand, UserResult> registerUserUseCase(
            UserRepository userRepository,
            PasswordEncoder passwordEncoder,
            UserMapper userMapper) {
        return new RegisterUserUseCase(userRepository, passwordEncoder, userMapper);
    }

    @Bean
    public UseCase<LoginCommand, LoginResult> loginUseCase(
            UserRepository userRepository,
            PasswordEncoder passwordEncoder,
            RefreshTokenStore refreshTokenStore) {
        return new LoginUseCase(userRepository, passwordEncoder, refreshTokenStore);
    }

    @Bean
    public UseCase<String, LoginResult> refreshUseCase(
            UserRepository userRepository,
            RefreshTokenStore refreshTokenStore) {
        return new RefreshUseCase(userRepository, refreshTokenStore);
    }

    @Bean
    public UseCase<String, Void> logoutUseCase(RefreshTokenStore refreshTokenStore) {
        return new LogoutUseCase(refreshTokenStore);
    }

    @Bean
    public UseCase<ChangeUserRoleCommand, UserResult> changeUserRoleUseCase(
            UserRepository userRepository,
            UserMapper userMapper) {
        return new ChangeUserRoleUseCase(userRepository, userMapper);
    }

    @Bean
    public UseCase<UUID, UserResult> getUserUseCase(
            UserRepository userRepository,
            UserMapper userMapper) {
        return new GetUserUseCase(userRepository, userMapper);
    }

    @Bean
    public UseCase<CreateEventCommand, EventResult> createEventUseCase(
            EventRepository eventRepository, EventMapper eventMapper) {
        return new CreateEventUseCase(eventRepository, eventMapper);
    }

    @Bean
    public UseCase<PublishEventCommand, EventResult> publishEventUseCase(
            EventRepository eventRepository, EventMapper eventMapper) {
        return new PublishEventUseCase(eventRepository, eventMapper);
    }

    @Bean
    public StartSaleUseCase startSaleUseCase(
            EventRepository eventRepository,
            TicketTypeRepository ticketTypeRepository,
            StockCachePort stockCachePort,
            EventMapper eventMapper) {
        return new StartSaleUseCase(eventRepository, ticketTypeRepository, stockCachePort, eventMapper);
    }

    @Bean
    @ConditionalOnProperty(
            prefix = "ticketing.messaging",
            name = {"enabled", "relay-enabled"},
            havingValue = "true",
            matchIfMissing = true)
    public RunReservationRelayUseCase runReservationRelayUseCase(ReservationRelayPort reservationRelayPort) {
        return new RunReservationRelayUseCase(reservationRelayPort);
    }

    @Bean
    public UseCase<UpdateEventCommand, EventResult> updateEventUseCase(
            EventRepository eventRepository, EventMapper eventMapper) {
        return new UpdateEventUseCase(eventRepository, eventMapper);
    }

    @Bean
    public UseCase<UUID, EventResult> getEventUseCase(EventRepository eventRepository, EventMapper eventMapper) {
        return new GetEventUseCase(eventRepository, eventMapper);
    }

    @Bean
    public UseCase<PageRequest, PageResult<EventResult>> listEventsUseCase(
            EventRepository eventRepository, EventMapper eventMapper) {
        return new ListEventsUseCase(eventRepository, eventMapper);
    }

    @Bean
    public UseCase<DeactivateEventCommand, EventResult> deactivateEventUseCase(
            EventRepository eventRepository, EventMapper eventMapper) {
        return new DeactivateEventUseCase(eventRepository, eventMapper);
    }

    @Bean
    public UseCase<CreateTicketTypeCommand, TicketTypeResult> createTicketTypeUseCase(
            TicketTypeRepository ticketTypeRepository, EventRepository eventRepository,
            TicketTypeMapper ticketTypeMapper) {
        return new CreateTicketTypeUseCase(ticketTypeRepository, eventRepository, ticketTypeMapper);
    }

    @Bean
    public UseCase<UpdateTicketTypeCommand, TicketTypeResult> updateTicketTypeUseCase(
            TicketTypeRepository ticketTypeRepository, EventRepository eventRepository,
            TicketTypeMapper ticketTypeMapper) {
        return new UpdateTicketTypeUseCase(ticketTypeRepository, eventRepository, ticketTypeMapper);
    }

    @Bean
    public UseCase<UUID, TicketTypeResult> getTicketTypeUseCase(
            TicketTypeRepository ticketTypeRepository, TicketTypeMapper ticketTypeMapper) {
        return new GetTicketTypeUseCase(ticketTypeRepository, ticketTypeMapper);
    }

    @Bean
    public UseCase<UUID, List<TicketTypeResult>> listTicketTypesByEventUseCase(
            TicketTypeRepository ticketTypeRepository, TicketTypeMapper ticketTypeMapper) {
        return new ListTicketTypesByEventUseCase(ticketTypeRepository, ticketTypeMapper);
    }

    @Bean
    public UseCase<DeactivateTicketTypeCommand, TicketTypeResult> deactivateTicketTypeUseCase(
            TicketTypeRepository ticketTypeRepository, EventRepository eventRepository,
            TicketTypeMapper ticketTypeMapper) {
        return new DeactivateTicketTypeUseCase(ticketTypeRepository, eventRepository, ticketTypeMapper);
    }

    @Bean
    public UseCase<ReserveTicketCommand, ReserveTicketResult> reserveTicketUseCase(
            EventRepository eventRepository,
            TicketTypeRepository ticketTypeRepository,
            StockCachePort stockCachePort) {
        return new ReserveTicketUseCase(eventRepository, ticketTypeRepository, stockCachePort);
    }

    @Bean
    public UseCase<GetOrderStatusCommand, OrderStatusResult> getOrderStatusUseCase(
            OrderRepository orderRepository,
            PaymentRepository paymentRepository,
            ReservationIntentStatusPort reservationIntentStatusPort) {
        return new GetOrderStatusUseCase(orderRepository, paymentRepository, reservationIntentStatusPort);
    }

    @Bean
    public CreateOrderFromReservationUseCase createOrderFromReservationUseCase(
            OrderRepository orderRepository,
            OutboxEventRepository outboxEventRepository,
            InitiatePaymentUseCase initiatePaymentUseCase) {
        return new CreateOrderFromReservationUseCase(
                orderRepository, outboxEventRepository, initiatePaymentUseCase);
    }

    @Bean
    public ExpireOrderTransaction expireOrderTransaction(
            OrderRepository orderRepository,
            OutboxEventRepository outboxEventRepository) {
        return new ExpireOrderTransaction(orderRepository, outboxEventRepository);
    }

    @Bean
    public ExpireOrderUseCase expireOrderUseCase(
            ExpireOrderTransaction expireOrderTransaction,
            ReservationIntentStore reservationIntentStore,
            StockCachePort stockCachePort) {
        return new ExpireOrderUseCase(
                expireOrderTransaction,
                reservationIntentStore,
                stockCachePort);
    }

    @Bean
    public TicketCodeGenerator ticketCodeGenerator() {
        return new UuidTicketCodeGenerator();
    }

    @Bean
    public InitiatePaymentUseCase initiatePaymentUseCase(
            OrderRepository orderRepository,
            PaymentRepository paymentRepository,
            PaymentGatewayPort paymentGatewayPort) {
        return new InitiatePaymentUseCase(orderRepository, paymentRepository, paymentGatewayPort);
    }

    @Bean
    public PaymentConfirmationTransaction paymentConfirmationTransaction(
            PaymentRepository paymentRepository,
            OrderRepository orderRepository,
            TicketTypeRepository ticketTypeRepository,
            TicketRepository ticketRepository,
            OutboxEventRepository outboxEventRepository,
            TicketCodeGenerator ticketCodeGenerator) {
        return new PaymentConfirmationTransaction(
                paymentRepository,
                orderRepository,
                ticketTypeRepository,
                ticketRepository,
                outboxEventRepository,
                ticketCodeGenerator);
    }

    @Bean
    public ConfirmPaymentUseCase confirmPaymentUseCase(
            PaymentConfirmationTransaction paymentConfirmationTransaction,
            ReservationIntentStore reservationIntentStore,
            StockCachePort stockCachePort) {
        return new ConfirmPaymentUseCase(
                paymentConfirmationTransaction,
                reservationIntentStore,
                stockCachePort);
    }
}
