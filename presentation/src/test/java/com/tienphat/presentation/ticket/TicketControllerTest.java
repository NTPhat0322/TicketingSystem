package com.tienphat.presentation.ticket;

import com.tienphat.application.auth.AuthorizationContext;
import com.tienphat.application.ticket.ListMyTicketsCommand;
import com.tienphat.application.ticket.TicketResult;
import com.tienphat.application.usecase.UseCase;
import com.tienphat.domain.model.TicketStatus;
import com.tienphat.domain.model.UserRole;
import com.tienphat.domain.repository.PageRequest;
import com.tienphat.domain.repository.PageResult;
import com.tienphat.presentation.config.SecurityConfig;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(TicketController.class)
@Import({SecurityConfig.class, TicketDtoMapperImpl.class})
class TicketControllerTest {

    private static final UUID USER_ID = UUID.fromString("0199c1b0-2d25-7a2c-8c44-7bb7b92e6d21");
    private static final Instant ISSUED_AT = Instant.parse("2026-10-01T08:00:00Z");

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private UseCase<ListMyTicketsCommand, PageResult<TicketResult>> listMyTicketsUseCase;

    @Test
    void listMineUsesDefaultPagingAndReturnsEnvelope() throws Exception {
        UUID ticketId = UUID.randomUUID();
        when(listMyTicketsUseCase.execute(any())).thenReturn(new PageResult<>(List.of(new TicketResult(
                ticketId, "TKT-1", TicketStatus.ISSUED, ISSUED_AT, null, UUID.randomUUID(), UUID.randomUUID(),
                "VIP", UUID.randomUUID(), "Rock Night", "Hall A", ISSUED_AT, ISSUED_AT.plusSeconds(7200))),
                0, 20, 1));

        mockMvc.perform(get("/api/v1/tickets").with(jwtFor("CUSTOMER")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].ticketId").value(ticketId.toString()))
                .andExpect(jsonPath("$.content[0].status").value("ISSUED"))
                .andExpect(jsonPath("$.content[0].venueName").value("Hall A"))
                .andExpect(jsonPath("$.totalElements").value(1))
                .andExpect(jsonPath("$.totalPages").value(1));

        ArgumentCaptor<ListMyTicketsCommand> captor = ArgumentCaptor.forClass(ListMyTicketsCommand.class);
        verify(listMyTicketsUseCase).execute(captor.capture());
        assertThat(captor.getValue().actor()).isEqualTo(new AuthorizationContext(USER_ID, UserRole.CUSTOMER));
        assertThat(captor.getValue().pageRequest()).isEqualTo(new PageRequest(0, 20));
    }

    @Test
    void listMineRejectsNegativePage() throws Exception {
        mockMvc.perform(get("/api/v1/tickets").param("page", "-1").with(jwtFor("CUSTOMER")))
                .andExpect(status().isBadRequest());

        verifyNoInteractions(listMyTicketsUseCase);
    }

    @Test
    void listMineRequiresAuthentication() throws Exception {
        mockMvc.perform(get("/api/v1/tickets"))
                .andExpect(status().isUnauthorized());

        verifyNoInteractions(listMyTicketsUseCase);
    }

    private static RequestPostProcessor jwtFor(String role) {
        return jwt()
                .jwt(jwt -> jwt.subject(USER_ID.toString()).claim("role", role))
                .authorities(new SimpleGrantedAuthority("ROLE_" + role));
    }
}
