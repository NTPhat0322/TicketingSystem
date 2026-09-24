package com.tienphat.presentation.tickettype;

import com.tienphat.application.auth.AuthorizationContext;
import com.tienphat.application.tickettype.CreateTicketTypeCommand;
import com.tienphat.application.tickettype.TicketTypeResult;
import com.tienphat.application.tickettype.UpdateTicketTypeCommand;
import com.tienphat.domain.model.TicketTypeStatus;
import com.tienphat.domain.model.UserRole;
import com.tienphat.presentation.tickettype.dto.CreateTicketTypeRequest;
import com.tienphat.presentation.tickettype.dto.TicketTypeResponse;
import com.tienphat.presentation.tickettype.dto.UpdateTicketTypeRequest;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mapstruct.factory.Mappers;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class TicketTypeDtoMapperTest {

    private final TicketTypeDtoMapper mapper = Mappers.getMapper(TicketTypeDtoMapper.class);

    @Test
    @DisplayName("toCommand(CreateTicketTypeRequest, actor) preserves every field and actor")
    void toCommand_fromCreateRequest_preservesEveryField() {
        UUID eventId = UUID.randomUUID();
        CreateTicketTypeRequest request = new CreateTicketTypeRequest(
                eventId, "VIP", BigDecimal.valueOf(150), 100, 4, 900);
        AuthorizationContext actor = new AuthorizationContext(UUID.randomUUID(), UserRole.ORGANIZER);

        CreateTicketTypeCommand command = mapper.toCommand(request, actor);

        assertThat(command.eventId()).isEqualTo(eventId);
        assertThat(command.actor()).isEqualTo(actor);
        assertThat(command.name()).isEqualTo("VIP");
        assertThat(command.price()).isEqualByComparingTo(BigDecimal.valueOf(150));
        assertThat(command.totalQuantity()).isEqualTo(100);
        assertThat(command.maxPerUser()).isEqualTo(4);
        assertThat(command.holdDurationSec()).isEqualTo(900);
    }

    @Test
    @DisplayName("toCommand(id, UpdateTicketTypeRequest, actor) preserves the path id plus every body field")
    void toCommand_fromUpdateRequest_preservesPathIdAndEveryField() {
        UUID id = UUID.randomUUID();
        UpdateTicketTypeRequest request = new UpdateTicketTypeRequest(
                "VIP v2", BigDecimal.valueOf(200), 120, 6, 600);

        AuthorizationContext actor = new AuthorizationContext(UUID.randomUUID(), UserRole.ORGANIZER);
        UpdateTicketTypeCommand command = mapper.toCommand(id, request, actor);

        assertThat(command.id()).isEqualTo(id);
        assertThat(command.actor()).isEqualTo(actor);
        assertThat(command.name()).isEqualTo("VIP v2");
        assertThat(command.price()).isEqualByComparingTo(BigDecimal.valueOf(200));
        assertThat(command.totalQuantity()).isEqualTo(120);
        assertThat(command.maxPerUser()).isEqualTo(6);
        assertThat(command.holdDurationSec()).isEqualTo(600);
    }

    @Test
    @DisplayName("toResponse() preserves every field including soldQuantity/version/status")
    void toResponse_preservesEveryField() {
        Instant now = Instant.now();
        TicketTypeResult result = new TicketTypeResult(
                UUID.randomUUID(), UUID.randomUUID(), "VIP", BigDecimal.valueOf(150),
                100, 42, 4, 900, 3, TicketTypeStatus.ACTIVE, now, now);

        TicketTypeResponse response = mapper.toResponse(result);

        assertThat(response.id()).isEqualTo(result.id());
        assertThat(response.eventId()).isEqualTo(result.eventId());
        assertThat(response.name()).isEqualTo(result.name());
        assertThat(response.price()).isEqualByComparingTo(result.price());
        assertThat(response.totalQuantity()).isEqualTo(result.totalQuantity());
        assertThat(response.soldQuantity()).isEqualTo(result.soldQuantity());
        assertThat(response.maxPerUser()).isEqualTo(result.maxPerUser());
        assertThat(response.holdDurationSec()).isEqualTo(result.holdDurationSec());
        assertThat(response.version()).isEqualTo(result.version());
        assertThat(response.status()).isEqualTo(result.status());
        assertThat(response.createdAt()).isEqualTo(result.createdAt());
        assertThat(response.updatedAt()).isEqualTo(result.updatedAt());
    }
}
