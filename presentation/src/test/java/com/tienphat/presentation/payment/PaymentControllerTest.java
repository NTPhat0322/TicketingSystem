package com.tienphat.presentation.payment;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.tienphat.application.payment.ConfirmPaymentUseCase;
import com.tienphat.application.payment.PaymentCallbackOutcome;
import com.tienphat.application.payment.PaymentCallbackResult;
import com.tienphat.domain.exception.ForbiddenOperationException;
import com.tienphat.domain.model.OrderStatus;
import com.tienphat.domain.model.PaymentStatus;
import com.tienphat.presentation.config.SecurityConfig;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

import java.math.BigDecimal;
import java.util.Map;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(PaymentController.class)
@Import(SecurityConfig.class)
class PaymentControllerTest {

    private static final UUID USER_ID = UUID.fromString("0199c1b0-2d25-7a2c-8c44-7bb7b92e6d21");
    private static final UUID ORDER_ID = UUID.fromString("0199c1b0-2d25-7a2c-8c44-7bb7b92e6d22");
    private static final UUID PAYMENT_ID = UUID.fromString("0199c1b0-2d25-7a2c-8c44-7bb7b92e6d23");

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private ConfirmPaymentUseCase confirmPaymentUseCase;

    private final ObjectMapper objectMapper = new ObjectMapper().findAndRegisterModules();

    @Test
    void callbackReturnsStableSuccessResponse() throws Exception {
        when(confirmPaymentUseCase.execute(any())).thenReturn(new PaymentCallbackResult(
                PAYMENT_ID,
                ORDER_ID,
                "LOCAL-transaction",
                PaymentStatus.SUCCESS,
                OrderStatus.PAID,
                PaymentCallbackOutcome.SUCCESS,
                2));

        mockMvc.perform(post("/api/v1/payments/callback")
                        .with(jwtFor("CUSTOMER"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "transactionRef", "LOCAL-transaction",
                                "provider", "LOCAL",
                                "amount", new BigDecimal("500000.00"),
                                "success", true))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.paymentId").value(PAYMENT_ID.toString()))
                .andExpect(jsonPath("$.orderStatus").value("PAID"))
                .andExpect(jsonPath("$.outcome").value("SUCCESS"))
                .andExpect(jsonPath("$.ticketCount").value(2));

        verify(confirmPaymentUseCase).execute(any());
    }

    @Test
    void foreignOwnerIsMappedToForbidden() throws Exception {
        when(confirmPaymentUseCase.execute(any()))
                .thenThrow(new ForbiddenOperationException("foreign order"));

        mockMvc.perform(post("/api/v1/payments/callback")
                        .with(jwtFor("CUSTOMER"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "transactionRef", "LOCAL-transaction",
                                "provider", "LOCAL",
                                "amount", new BigDecimal("500000.00"),
                                "success", true))))
                .andExpect(status().isForbidden());
    }

    @Test
    void malformedCallbackIsRejectedBeforeUseCase() throws Exception {
        mockMvc.perform(post("/api/v1/payments/callback")
                        .with(jwtFor("CUSTOMER"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"provider\":\"LOCAL\",\"success\":true}"))
                .andExpect(status().isBadRequest());

        verifyNoInteractions(confirmPaymentUseCase);
    }

    @Test
    void unauthenticatedCallbackIsRejected() throws Exception {
        mockMvc.perform(post("/api/v1/payments/callback")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "transactionRef", "LOCAL-transaction",
                                "provider", "LOCAL",
                                "amount", new BigDecimal("500000.00"),
                                "success", true))))
                .andExpect(status().isUnauthorized());

        verifyNoInteractions(confirmPaymentUseCase);
    }

    private static RequestPostProcessor jwtFor(String role) {
        return jwt()
                .jwt(jwt -> jwt.subject(USER_ID.toString()).claim("role", role));
    }
}
