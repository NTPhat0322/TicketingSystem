package com.tienphat.application.reservation;

/** Outbound boundary for manually running one batch of due reservation intents. */
@FunctionalInterface
public interface ReservationRelayPort {

    int relayDueReservations();
}
