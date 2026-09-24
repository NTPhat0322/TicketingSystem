package com.tienphat.presentation.auth;

import com.tienphat.application.auth.LoginCommand;
import com.tienphat.application.auth.LoginResult;
import com.tienphat.application.user.RegisterUserCommand;
import com.tienphat.domain.model.UserRole;
import com.tienphat.presentation.auth.dto.LoginRequest;
import com.tienphat.presentation.auth.dto.RegisterRequest;
import com.tienphat.presentation.auth.dto.TokenResponse;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mapstruct.factory.Mappers;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class AuthDtoMapperTest {

    private final AuthDtoMapper mapper = Mappers.getMapper(AuthDtoMapper.class);

    @Test
    @DisplayName("RegisterRequest maps every registration field to RegisterUserCommand")
    void toCommand_fromRegisterRequest_preservesEveryField() {
        RegisterRequest request = new RegisterRequest(
                "alice@example.com", "password123", "Alice Nguyen", "+84123456789");

        RegisterUserCommand command = mapper.toCommand(request);

        assertThat(command.email()).isEqualTo(request.email());
        assertThat(command.password()).isEqualTo(request.password());
        assertThat(command.fullName()).isEqualTo(request.fullName());
        assertThat(command.phone()).isEqualTo(request.phone());
    }

    @Test
    @DisplayName("LoginRequest maps email and password to LoginCommand")
    void toCommand_fromLoginRequest_preservesCredentials() {
        LoginRequest request = new LoginRequest("alice@example.com", "password123");

        LoginCommand command = mapper.toCommand(request);

        assertThat(command.email()).isEqualTo(request.email());
        assertThat(command.password()).isEqualTo(request.password());
    }

    @Test
    @DisplayName("toTokenResponse combines the issued access token with the raw refresh token")
    void toTokenResponse_combinesBothTokens() {
        LoginResult result = new LoginResult(UUID.randomUUID(), UserRole.CUSTOMER, "refresh-token");

        TokenResponse response = mapper.toTokenResponse(result, "access-token");

        assertThat(response.accessToken()).isEqualTo("access-token");
        assertThat(response.refreshToken()).isEqualTo("refresh-token");
    }
}
