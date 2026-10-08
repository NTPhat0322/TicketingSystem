package com.tienphat.application.reservation;

import com.tienphat.application.usecase.UseCase;
import com.tienphat.domain.exception.InvalidReservationRequestException;

/** Runs one admin-triggered relay batch; normal delivery remains scheduled and retryable. */
public class RunReservationRelayUseCase implements UseCase<RunReservationRelayCommand, Integer> {

    private final ReservationRelayPort reservationRelayPort;

    public RunReservationRelayUseCase(ReservationRelayPort reservationRelayPort) {
        this.reservationRelayPort = reservationRelayPort;
    }

    @Override
    public Integer execute(RunReservationRelayCommand command) {
        if (command == null || command.actor() == null) {
            throw new InvalidReservationRequestException("Relay command and actor must not be null");
        }
        command.actor().requireAdmin();
        return reservationRelayPort.relayDueReservations();
    }
}
