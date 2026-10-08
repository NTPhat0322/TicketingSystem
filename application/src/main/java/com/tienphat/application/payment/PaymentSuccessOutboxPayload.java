package com.tienphat.application.payment;

import com.tienphat.domain.model.Payment;
import com.tienphat.domain.model.Ticket;

import java.math.BigDecimal;
import java.util.List;

/** JSON payload for the durable PAYMENT_SUCCESS event. */
record PaymentSuccessOutboxPayload(Payment payment, List<Ticket> tickets) {

    String toJson() {
        String ticketIds = tickets.stream()
                .map(ticket -> "\"" + ticket.getId() + "\"")
                .reduce((left, right) -> left + "," + right)
                .orElse("");
        String ticketCodes = tickets.stream()
                .map(ticket -> "\"" + ticket.getTicketCode() + "\"")
                .reduce((left, right) -> left + "," + right)
                .orElse("");
        return "{" +
                "\"schemaVersion\":1," +
                "\"paymentId\":\"" + payment.getId() + "\"," +
                "\"orderId\":\"" + payment.getOrderId() + "\"," +
                "\"provider\":\"" + payment.getProvider() + "\"," +
                "\"transactionRef\":\"" + payment.getTransactionRef() + "\"," +
                "\"amount\":" + number(payment.getAmount().getAmount()) + "," +
                "\"ticketIds\":[" + ticketIds + "]," +
                "\"ticketCodes\":[" + ticketCodes + "]" +
                "}";
    }

    private static String number(BigDecimal value) {
        return value.toPlainString();
    }
}
