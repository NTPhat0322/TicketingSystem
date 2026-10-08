package com.tienphat.application.reservation;

import com.tienphat.application.auth.AuthorizationContext;
import com.tienphat.domain.exception.ForbiddenOperationException;
import com.tienphat.domain.model.UserRole;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class RunReservationRelayUseCaseTest {

    private final ReservationRelayPort relayPort = mock(ReservationRelayPort.class);
    private RunReservationRelayUseCase useCase;

    @BeforeEach
    void setUp() {
        useCase = new RunReservationRelayUseCase(relayPort);
    }

    @Test
    void executeAsAdminRelaysDueIntentsAndReturnsPublishedCount() {
        when(relayPort.relayDueReservations()).thenReturn(3);

        int published = useCase.execute(new RunReservationRelayCommand(
                new AuthorizationContext(UUID.randomUUID(), UserRole.ADMIN)));

        assertThat(published).isEqualTo(3);
        verify(relayPort).relayDueReservations();
    }

    @Test
    void executeRejectsNonAdminWithoutRunningRelay() {
        RunReservationRelayCommand command = new RunReservationRelayCommand(
                new AuthorizationContext(UUID.randomUUID(), UserRole.ORGANIZER));

        assertThatThrownBy(() -> useCase.execute(command))
                .isInstanceOf(ForbiddenOperationException.class);

        verify(relayPort, never()).relayDueReservations();
    }
}
