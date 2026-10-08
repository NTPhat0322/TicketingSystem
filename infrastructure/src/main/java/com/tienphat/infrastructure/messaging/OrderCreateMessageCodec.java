package com.tienphat.infrastructure.messaging;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Component;

/** Serializes the versioned order-create message as UTF-8 JSON. */
@Component
public class OrderCreateMessageCodec {

    private final ObjectMapper objectMapper = new ObjectMapper();

    public byte[] encode(OrderCreateMessage message) {
        try {
            return objectMapper.writeValueAsBytes(message);
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("Unable to serialize order-create message", exception);
        }
    }

    public OrderCreateMessage decode(byte[] body) {
        try {
            return objectMapper.readValue(body, OrderCreateMessage.class);
        } catch (Exception exception) {
            throw new IllegalArgumentException("Invalid order-create message", exception);
        }
    }
}
