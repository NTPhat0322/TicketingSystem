package com.tienphat.application.reservation;

import com.tienphat.application.auth.AuthorizationContext;

public record RunReservationRelayCommand(AuthorizationContext actor) {
}
