package com.tienphat.application.ticket;

import com.tienphat.application.auth.AuthorizationContext;

import java.util.UUID;

/** Tickets issued for one order; owner or ADMIN. */
public record GetOrderTicketsCommand(UUID orderId, AuthorizationContext actor) {
}
