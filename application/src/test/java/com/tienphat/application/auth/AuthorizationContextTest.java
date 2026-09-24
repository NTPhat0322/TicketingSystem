package com.tienphat.application.auth;

import com.tienphat.domain.exception.ForbiddenOperationException;
import com.tienphat.domain.model.UserRole;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

class AuthorizationContextTest {

    @Test
    @DisplayName("an ORGANIZER can manage an event that belongs to the same user")
    void organizerCanManageOwnEvent() {
        UUID userId = UUID.randomUUID();

        new AuthorizationContext(userId, UserRole.ORGANIZER).requireCanManage(userId);
    }

    @Test
    @DisplayName("an ORGANIZER cannot manage another user's event")
    void organizerCannotManageAnotherUsersEvent() {
        AuthorizationContext actor = new AuthorizationContext(UUID.randomUUID(), UserRole.ORGANIZER);

        assertThatThrownBy(() -> actor.requireCanManage(UUID.randomUUID()))
                .isInstanceOf(ForbiddenOperationException.class);
    }

    @Test
    @DisplayName("an ADMIN can manage any event")
    void adminCanManageAnyEvent() {
        new AuthorizationContext(UUID.randomUUID(), UserRole.ADMIN).requireCanManage(UUID.randomUUID());
    }

    @Test
    @DisplayName("a CUSTOMER cannot create or manage events")
    void customerCannotCreate() {
        AuthorizationContext actor = new AuthorizationContext(UUID.randomUUID(), UserRole.CUSTOMER);

        assertThatThrownBy(actor::requireCanCreate)
                .isInstanceOf(ForbiddenOperationException.class);
        assertThatThrownBy(() -> actor.requireCanManage(UUID.randomUUID()))
                .isInstanceOf(ForbiddenOperationException.class);
    }
}
