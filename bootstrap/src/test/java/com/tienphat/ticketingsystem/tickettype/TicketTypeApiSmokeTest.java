package com.tienphat.ticketingsystem.tickettype;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.tienphat.ticketingsystem.AbstractPostgresIntegrationTest;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.MOCK)
@AutoConfigureMockMvc
class TicketTypeApiSmokeTest extends AbstractPostgresIntegrationTest {

    private static final Instant NOW = Instant.now();
    private static final Instant SALE_START = NOW.plus(1, ChronoUnit.DAYS);
    private static final Instant SALE_END = SALE_START.plus(7, ChronoUnit.DAYS);
    private static final Instant START = SALE_END.plus(1, ChronoUnit.DAYS);
    private static final Instant END = START.plus(3, ChronoUnit.HOURS);

    @Autowired
    private MockMvc mockMvc;

    private final ObjectMapper objectMapper = new ObjectMapper().findAndRegisterModules();

    @Test
    void fullTicketTypeLifecycle_createReadListUpdateDeactivate() throws Exception {
        String eventId = createEvent();

        Map<String, Object> createBody = new LinkedHashMap<>();
        createBody.put("eventId", eventId);
        createBody.put("name", "VIP");
        createBody.put("price", 150);
        createBody.put("totalQuantity", 100);
        createBody.put("maxPerUser", 4);
        createBody.put("holdDurationSec", 900);

        MvcResult createResult = mockMvc.perform(post("/api/v1/ticket-types")
                        .contentType("application/json")
                        .content(objectMapper.writeValueAsString(createBody)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.name").value("VIP"))
                .andExpect(jsonPath("$.status").value("ACTIVE"))
                .andReturn();

        String ticketTypeId =
                objectMapper.readTree(createResult.getResponse().getContentAsString()).get("id").asText();

        Map<String, Object> createWithUnknownEvent = new LinkedHashMap<>(createBody);
        createWithUnknownEvent.put("eventId", UUID.randomUUID().toString());
        mockMvc.perform(post("/api/v1/ticket-types")
                        .contentType("application/json")
                        .content(objectMapper.writeValueAsString(createWithUnknownEvent)))
                .andExpect(status().isNotFound());

        mockMvc.perform(get("/api/v1/ticket-types/{id}", ticketTypeId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(ticketTypeId));

        mockMvc.perform(get("/api/v1/events/{eventId}/ticket-types", eventId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].id").value(ticketTypeId));

        Map<String, Object> updateBody = new LinkedHashMap<>();
        updateBody.put("name", "VIP v2");
        updateBody.put("price", 200);
        updateBody.put("totalQuantity", 120);
        updateBody.put("maxPerUser", 6);
        updateBody.put("holdDurationSec", 600);

        mockMvc.perform(put("/api/v1/ticket-types/{id}", ticketTypeId)
                        .contentType("application/json")
                        .content(objectMapper.writeValueAsString(updateBody)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("VIP v2"));

        mockMvc.perform(post("/api/v1/ticket-types/{id}/deactivate", ticketTypeId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("CLOSED"));
    }

    private String createEvent() throws Exception {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("organizerId", UUID.randomUUID().toString());
        body.put("name", "Smoke Test Event For TicketType");
        body.put("description", "e2e smoke test");
        body.put("venueName", "Main Hall");
        body.put("startTime", START.toString());
        body.put("endTime", END.toString());
        body.put("saleStartTime", SALE_START.toString());
        body.put("saleEndTime", SALE_END.toString());

        MvcResult result = mockMvc.perform(post("/api/v1/events")
                        .contentType("application/json")
                        .content(objectMapper.writeValueAsString(body)))
                .andExpect(status().isCreated())
                .andReturn();

        return objectMapper.readTree(result.getResponse().getContentAsString()).get("id").asText();
    }
}
