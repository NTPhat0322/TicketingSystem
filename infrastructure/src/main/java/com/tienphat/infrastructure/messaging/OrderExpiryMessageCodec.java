package com.tienphat.infrastructure.messaging;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Component;

/** Serializes the expiry message as UTF-8 JSON. */
@Component
public class OrderExpiryMessageCodec {

    private final ObjectMapper objectMapper = new ObjectMapper();

    public byte[] encode(OrderExpiryMessage message) {
        try {
            return objectMapper.writeValueAsBytes(message);
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("Unable to serialize order-expiry message", exception);
        }
    }

    public OrderExpiryMessage decode(byte[] body) {
        try {
            return objectMapper.readValue(body, OrderExpiryMessage.class);
        } catch (Exception exception) {
            throw new IllegalArgumentException("Invalid order-expiry message", exception);
        }
    }
}
