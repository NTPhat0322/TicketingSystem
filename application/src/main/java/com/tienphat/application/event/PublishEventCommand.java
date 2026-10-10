package com.tienphat.application.event;

import com.tienphat.application.auth.AuthorizationContext;

import java.util.UUID;

public record PublishEventCommand(UUID eventId, AuthorizationContext actor) {
}
