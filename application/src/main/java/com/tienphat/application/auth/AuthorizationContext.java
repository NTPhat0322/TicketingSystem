package com.tienphat.application.auth;

import com.tienphat.domain.exception.ForbiddenOperationException;
import com.tienphat.domain.model.UserRole;

import java.util.UUID;

/**
 * Identity and role of the caller crossing from the presentation layer into an application use
 * case. Keeping this value in the command makes ownership checks explicit and keeps application
 * code independent from Spring Security types.
 */
public record AuthorizationContext(UUID userId, UserRole role) {

    public void requireCanCreate() {
        requireAuthenticated();
        if (role != UserRole.ADMIN && role != UserRole.ORGANIZER) {
            throw new ForbiddenOperationException("Only ADMIN or ORGANIZER can perform this operation");
        }
    }

    public void requireCanManage(UUID ownerId) {
        requireCanCreate();
        if (role != UserRole.ADMIN && !userId.equals(ownerId)) {
            throw new ForbiddenOperationException("You do not own this event");
        }
    }

    public void requireAuthenticated() {
        if (userId == null || role == null) {
            throw new ForbiddenOperationException("An authenticated user is required");
        }
    }

    public void requireCanAccess(UUID ownerId) {
        requireAuthenticated();
        if (role != UserRole.ADMIN && !userId.equals(ownerId)) {
            throw new ForbiddenOperationException("You do not own this order");
        }
    }
}
