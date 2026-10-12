package com.tienphat.application.ticket;

import com.tienphat.application.auth.AuthorizationContext;
import com.tienphat.domain.repository.PageRequest;

/** Tickets held by the caller, most recently issued first. */
public record ListMyTicketsCommand(AuthorizationContext actor, PageRequest pageRequest) {
}
