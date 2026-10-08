package com.tienphat.application.order;

import com.tienphat.domain.model.Order;
import com.tienphat.domain.model.OrderItem;

import java.math.BigDecimal;

/** JSON payload for the ORDER_EXPIRED event without leaking a serializer into the domain. */
record OrderExpiredOutboxPayload(
        Order order,
        ExpireOrderCommand command) {

    String toJson() {
        OrderItem item = order.getItems().get(0);
        return "{" +
                "\"schemaVersion\":1," +
                "\"messageId\":\"" + command.messageId() + "\"," +
                "\"orderId\":\"" + order.getId() + "\"," +
                "\"userId\":\"" + order.getUserId() + "\"," +
                "\"eventId\":\"" + order.getEventId() + "\"," +
                "\"ticketTypeId\":\"" + item.getTicketTypeId() + "\"," +
                "\"quantity\":" + item.getQuantity() + "," +
                "\"unitPrice\":" + number(item.getUnitPrice().getAmount()) + "," +
                "\"reservedAt\":\"" + order.getReservedAt() + "\"," +
                "\"expiresAt\":\"" + order.getExpiresAt() + "\"," +
                "\"expiredAt\":\"" + order.getUpdatedAt() + "\"" +
                "}";
    }

    private static String number(BigDecimal value) {
        return value.toPlainString();
    }
}
