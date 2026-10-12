package com.tienphat.application.order;

import com.tienphat.application.auth.AuthorizationContext;
import com.tienphat.domain.repository.PageRequest;

/** The caller's own order history; ADMIN gets their own orders too, not everyone's. */
public record ListMyOrdersCommand(AuthorizationContext actor, PageRequest pageRequest) {
}
