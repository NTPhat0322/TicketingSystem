package com.tienphat.application.order;

import com.tienphat.domain.model.Order;
import com.tienphat.domain.model.OrderItem;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

/** Small JSON payload for the durable ORDER_CREATED event without leaking JSON into the domain. */
record OrderCreatedOutboxPayload(
        UUID messageId,
        Order order) {

    String toJson() {
        OrderItem item = order.getItems().get(0);
        return "{" +
                "\"schemaVersion\":1," +
                "\"messageId\":\"" + messageId + "\"," +
                "\"orderId\":\"" + order.getId() + "\"," +
                "\"userId\":\"" + order.getUserId() + "\"," +
                "\"eventId\":\"" + order.getEventId() + "\"," +
                "\"ticketTypeId\":\"" + item.getTicketTypeId() + "\"," +
                "\"quantity\":" + item.getQuantity() + "," +
                "\"unitPrice\":" + number(item.getUnitPrice().getAmount()) + "," +
                "\"totalAmount\":" + number(order.getTotalAmount().getAmount()) + "," +
                "\"reservedAt\":\"" + order.getReservedAt() + "\"," +
                "\"expiresAt\":\"" + order.getExpiresAt() + "\"," +
                "\"createdAt\":\"" + order.getCreatedAt() + "\"" +
                "}";
    }

    private static String number(BigDecimal value) {
        return value.toPlainString();
    }
}
