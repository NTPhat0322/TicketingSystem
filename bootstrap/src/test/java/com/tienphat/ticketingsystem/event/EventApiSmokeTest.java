package com.tienphat.ticketingsystem.event;

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
class EventApiSmokeTest extends AbstractPostgresIntegrationTest {

    private static final Instant NOW = Instant.now();
    private static final Instant SALE_START = NOW.plus(1, ChronoUnit.DAYS);
    private static final Instant SALE_END = SALE_START.plus(7, ChronoUnit.DAYS);
    private static final Instant START = SALE_END.plus(1, ChronoUnit.DAYS);
    private static final Instant END = START.plus(3, ChronoUnit.HOURS);

    @Autowired
    private MockMvc mockMvc;

    private final ObjectMapper objectMapper = new ObjectMapper().findAndRegisterModules();

    @Test
    void fullEventLifecycle_createReadListUpdateDeactivate() throws Exception {
        Map<String, Object> createBody = new LinkedHashMap<>();
        createBody.put("organizerId", UUID.randomUUID().toString());
        createBody.put("name", "Smoke Test Event");
        createBody.put("description", "e2e smoke test");
        createBody.put("venueName", "Main Hall");
        createBody.put("startTime", START.toString());
        createBody.put("endTime", END.toString());
        createBody.put("saleStartTime", SALE_START.toString());
        createBody.put("saleEndTime", SALE_END.toString());

        MvcResult createResult = mockMvc.perform(post("/api/v1/events")
                        .contentType("application/json")
                        .content(objectMapper.writeValueAsString(createBody)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.name").value("Smoke Test Event"))
                .andExpect(jsonPath("$.status").value("DRAFT"))
                .andReturn();

        String id = objectMapper.readTree(createResult.getResponse().getContentAsString()).get("id").asText();

        mockMvc.perform(get("/api/v1/events/{id}", id))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(id));

        mockMvc.perform(get("/api/v1/events"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content").isArray());

        Map<String, Object> updateBody = new LinkedHashMap<>();
        updateBody.put("name", "Smoke Test Event Updated");
        updateBody.put("description", "updated description");
        updateBody.put("venueName", "Main Hall 2");
        updateBody.put("startTime", START.toString());
        updateBody.put("endTime", END.toString());
        updateBody.put("saleStartTime", SALE_START.toString());
        updateBody.put("saleEndTime", SALE_END.toString());

        mockMvc.perform(put("/api/v1/events/{id}", id)
                        .contentType("application/json")
                        .content(objectMapper.writeValueAsString(updateBody)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("Smoke Test Event Updated"));

        mockMvc.perform(post("/api/v1/events/{id}/deactivate", id))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("CANCELLED"));

        mockMvc.perform(get("/api/v1/events/{id}", UUID.randomUUID()))
                .andExpect(status().isNotFound());
    }
}
