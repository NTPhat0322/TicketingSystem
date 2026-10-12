package com.tienphat.presentation.ticket;

import com.tienphat.application.ticket.ListMyTicketsCommand;
import com.tienphat.application.ticket.TicketResult;
import com.tienphat.application.usecase.UseCase;
import com.tienphat.domain.repository.PageRequest;
import com.tienphat.domain.repository.PageResult;
import com.tienphat.presentation.auth.JwtAuthorizationContext;
import com.tienphat.presentation.config.OpenApiConfig;
import com.tienphat.presentation.dto.PageResponse;
import com.tienphat.presentation.ticket.dto.TicketResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@Validated
@RequestMapping("/api/v1/tickets")
@SecurityRequirement(name = OpenApiConfig.BEARER_AUTH_SCHEME)
public class TicketController {

    private final UseCase<ListMyTicketsCommand, PageResult<TicketResult>> listMyTicketsUseCase;
    private final TicketDtoMapper mapper;

    public TicketController(
            UseCase<ListMyTicketsCommand, PageResult<TicketResult>> listMyTicketsUseCase,
            TicketDtoMapper mapper) {
        this.listMyTicketsUseCase = listMyTicketsUseCase;
        this.mapper = mapper;
    }

    @GetMapping
    @Operation(summary = "List the caller's tickets, most recently issued first")
    public PageResponse<TicketResponse> listMine(
            @RequestParam(defaultValue = "0") @Min(0) int page,
            @RequestParam(defaultValue = "20") @Min(1) @Max(100) int size,
            @AuthenticationPrincipal Jwt jwt) {
        PageResult<TicketResult> result = listMyTicketsUseCase.execute(
                new ListMyTicketsCommand(JwtAuthorizationContext.from(jwt), new PageRequest(page, size)));
        return mapper.toPageResponse(result);
    }
}
