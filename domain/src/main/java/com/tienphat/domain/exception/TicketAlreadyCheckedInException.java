package com.tienphat.domain.exception;

/**
 * The same ticket was scanned twice at the gate.
 *
 * <p>A distinct type rather than an {@link InvalidTicketStateException} because the two mean
 * opposite things to the person holding the scanner: this one says the holder is already inside and
 * carries when they entered, so staff can decide whether it is a double scan or a shared QR code.
 * A cancelled ticket is simply refused.
 *
 * <p>Same shape as {@link DuplicatePaymentException}: a repeat of a legitimate action gets its own
 * exception so the caller never has to inspect {@code status} to tell the cases apart.
 */
public class TicketAlreadyCheckedInException extends DomainException {

    public TicketAlreadyCheckedInException(String message) {
        super(message);
    }
}
