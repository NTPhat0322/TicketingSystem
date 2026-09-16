package com.tienphat.domain.model;

/**
 * Plain attribute, not a lifecycle state machine — any role can become any other role, so there
 * is deliberately no transition table here (unlike {@code EventStatus}, {@code OrderStatus}, etc.).
 */
public enum UserRole {
    CUSTOMER,
    ORGANIZER,
    ADMIN
}
