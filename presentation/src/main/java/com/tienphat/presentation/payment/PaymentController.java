package com.tienphat.presentation.payment;

import com.tienphat.application.auth.AuthorizationContext;
import com.tienphat.application.payment.ConfirmPaymentCommand;
import com.tienphat.application.payment.ConfirmPaymentUseCase;
import com.tienphat.application.payment.PaymentCallbackResult;
import com.tienphat.domain.vo.Money;
import com.tienphat.presentation.auth.JwtAuthorizationContext;
import com.tienphat.presentation.config.OpenApiConfig;
import com.tienphat.presentation.payment.dto.PaymentCallbackRequest;
import com.tienphat.presentation.payment.dto.PaymentCallbackResponse;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import jakarta.validation.Valid;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/payments")
@SecurityRequirement(name = OpenApiConfig.BEARER_AUTH_SCHEME)
public class PaymentController {

    private final ConfirmPaymentUseCase confirmPaymentUseCase;

    public PaymentController(ConfirmPaymentUseCase confirmPaymentUseCase) {
        this.confirmPaymentUseCase = confirmPaymentUseCase;
    }

    @PostMapping("/callback")
    public PaymentCallbackResponse callback(
            @Valid @RequestBody PaymentCallbackRequest request,
            @AuthenticationPrincipal Jwt jwt) {
        AuthorizationContext actor = JwtAuthorizationContext.from(jwt);
        PaymentCallbackResult result = confirmPaymentUseCase.execute(new ConfirmPaymentCommand(
                actor,
                request.transactionRef(),
                request.provider(),
                Money.of(request.amount()),
                request.success()));
        return new PaymentCallbackResponse(
                result.paymentId(),
                result.orderId(),
                result.transactionRef(),
                result.paymentStatus(),
                result.orderStatus(),
                result.outcome(),
                result.ticketCount());
    }
}
