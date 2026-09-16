package com.tienphat.domain.exception;

/**
 * A ticket was asked for a transition its current status does not allow — checking in a cancelled
 * ticket, or voiding one that has already passed the gate.
 *
 * <p>Deliberately not raised for a repeat check-in; that is
 * {@link TicketAlreadyCheckedInException}, which the gate handles differently.
 */
public class InvalidTicketStateException extends DomainException {

    public InvalidTicketStateException(String message) {
        super(message);
    }
}
