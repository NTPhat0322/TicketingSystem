package com.tienphat.domain.exception;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class DomainExceptionErrorTypeTest {

    @Test
    @DisplayName("EventNotFoundException maps to NOT_FOUND")
    void eventNotFound_mapsToNotFound() {
        assertThat(new EventNotFoundException("not found").errorType()).isEqualTo(ErrorType.NOT_FOUND);
    }

    @Test
    @DisplayName("TicketTypeNotFoundException maps to NOT_FOUND")
    void ticketTypeNotFound_mapsToNotFound() {
        assertThat(new TicketTypeNotFoundException("not found").errorType()).isEqualTo(ErrorType.NOT_FOUND);
    }

    @Test
    @DisplayName("TicketTypeConcurrentUpdateException maps to CONFLICT")
    void ticketTypeConcurrentUpdate_mapsToConflict() {
        assertThat(new TicketTypeConcurrentUpdateException("conflict").errorType()).isEqualTo(ErrorType.CONFLICT);
    }

    @Test
    @DisplayName("TicketTypeNotAvailableException maps to CONFLICT")
    void ticketTypeNotAvailable_mapsToConflict() {
        assertThat(new TicketTypeNotAvailableException("conflict").errorType()).isEqualTo(ErrorType.CONFLICT);
    }

    @Test
    @DisplayName("InvalidEventDataException maps to VALIDATION")
    void invalidEventData_mapsToValidation() {
        assertThat(new InvalidEventDataException("invalid").errorType()).isEqualTo(ErrorType.VALIDATION);
    }

    @Test
    @DisplayName("InvalidEventScheduleException maps to VALIDATION")
    void invalidEventSchedule_mapsToValidation() {
        assertThat(new InvalidEventScheduleException("invalid").errorType()).isEqualTo(ErrorType.VALIDATION);
    }

    @Test
    @DisplayName("InvalidEventStateException maps to VALIDATION")
    void invalidEventState_mapsToValidation() {
        assertThat(new InvalidEventStateException("invalid").errorType()).isEqualTo(ErrorType.VALIDATION);
    }

    @Test
    @DisplayName("InvalidTicketTypeDataException maps to VALIDATION")
    void invalidTicketTypeData_mapsToValidation() {
        assertThat(new InvalidTicketTypeDataException("invalid").errorType()).isEqualTo(ErrorType.VALIDATION);
    }

    @Test
    @DisplayName("UserNotFoundException maps to NOT_FOUND")
    void userNotFound_mapsToNotFound() {
        assertThat(new UserNotFoundException("not found").errorType()).isEqualTo(ErrorType.NOT_FOUND);
    }

    @Test
    @DisplayName("DuplicateEmailException maps to CONFLICT")
    void duplicateEmail_mapsToConflict() {
        assertThat(new DuplicateEmailException("conflict").errorType()).isEqualTo(ErrorType.CONFLICT);
    }

    @Test
    @DisplayName("InvalidCredentialsException maps to UNAUTHORIZED")
    void invalidCredentials_mapsToUnauthorized() {
        assertThat(new InvalidCredentialsException("invalid").errorType()).isEqualTo(ErrorType.UNAUTHORIZED);
    }

    @Test
    @DisplayName("InvalidRefreshTokenException maps to UNAUTHORIZED")
    void invalidRefreshToken_mapsToUnauthorized() {
        assertThat(new InvalidRefreshTokenException("invalid").errorType()).isEqualTo(ErrorType.UNAUTHORIZED);
    }

    @Test
    @DisplayName("ForbiddenOperationException maps to FORBIDDEN")
    void forbiddenOperation_mapsToForbidden() {
        assertThat(new ForbiddenOperationException("forbidden").errorType()).isEqualTo(ErrorType.FORBIDDEN);
    }

    @Test
    @DisplayName("a DomainException subclass that does not override errorType() defaults to INTERNAL")
    void unmappedSubclass_defaultsToInternal() {
        DomainException unmapped = new DomainException("boom") {};

        assertThat(unmapped.errorType()).isEqualTo(ErrorType.INTERNAL);
    }
}
