package com.tienphat.application.tickettype;

import com.tienphat.application.auth.AuthorizationContext;

import java.util.UUID;

public record DeactivateTicketTypeCommand(UUID id, AuthorizationContext actor) {
}
