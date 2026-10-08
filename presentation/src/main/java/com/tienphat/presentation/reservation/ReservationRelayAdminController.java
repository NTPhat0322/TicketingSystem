package com.tienphat.presentation.reservation;

import com.tienphat.application.reservation.RunReservationRelayCommand;
import com.tienphat.application.reservation.RunReservationRelayUseCase;
import com.tienphat.presentation.auth.JwtAuthorizationContext;
import com.tienphat.presentation.config.OpenApiConfig;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/admin/reservations")
@ConditionalOnProperty(
        prefix = "ticketing.messaging",
        name = {"enabled", "relay-enabled"},
        havingValue = "true",
        matchIfMissing = true)
@SecurityRequirement(name = OpenApiConfig.BEARER_AUTH_SCHEME)
public class ReservationRelayAdminController {

    private final RunReservationRelayUseCase runReservationRelayUseCase;

    public ReservationRelayAdminController(RunReservationRelayUseCase runReservationRelayUseCase) {
        this.runReservationRelayUseCase = runReservationRelayUseCase;
    }

    @PostMapping("/relay")
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(summary = "Run one batch of due reservation intents")
    public ReservationRelayResponse relay(@AuthenticationPrincipal Jwt jwt) {
        int publishedCount = runReservationRelayUseCase.execute(
                new RunReservationRelayCommand(JwtAuthorizationContext.from(jwt)));
        return new ReservationRelayResponse(publishedCount);
    }
}
