package com.tienphat.presentation.auth;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.tienphat.application.auth.LoginCommand;
import com.tienphat.application.auth.LoginResult;
import com.tienphat.application.user.RegisterUserCommand;
import com.tienphat.application.user.UserResult;
import com.tienphat.application.usecase.UseCase;
import com.tienphat.domain.exception.DuplicateEmailException;
import com.tienphat.domain.exception.InvalidCredentialsException;
import com.tienphat.domain.exception.InvalidRefreshTokenException;
import com.tienphat.domain.model.UserRole;
import com.tienphat.presentation.auth.dto.LoginRequest;
import com.tienphat.presentation.auth.dto.LogoutRequest;
import com.tienphat.presentation.auth.dto.RefreshRequest;
import com.tienphat.presentation.config.SecurityConfig;
import com.tienphat.presentation.user.UserDtoMapperImpl;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(AuthController.class)
@Import({SecurityConfig.class, AuthDtoMapperImpl.class, UserDtoMapperImpl.class})
class AuthControllerTest {

    private static final UUID USER_ID = UUID.fromString("018f0f9e-0e39-7f31-9e13-ec7c1f160001");
    private static final Instant NOW = Instant.parse("2026-01-01T00:00:00Z");

    @Autowired
    private MockMvc mockMvc;

    private final ObjectMapper objectMapper = new ObjectMapper().findAndRegisterModules();

    @MockitoBean
    private UseCase<RegisterUserCommand, UserResult> registerUserUseCase;

    @MockitoBean
    private UseCase<LoginCommand, LoginResult> loginUseCase;

    @MockitoBean
    private UseCase<String, LoginResult> refreshUseCase;

    @MockitoBean
    private UseCase<String, Void> logoutUseCase;

    @MockitoBean
    private AccessTokenIssuer accessTokenIssuer;

    private static UserResult customerResult() {
        return new UserResult(USER_ID, "alice@example.com", "Alice Nguyen", "+84123456789",
                UserRole.CUSTOMER, NOW, NOW);
    }

    private static LoginResult loginResult(String refreshToken) {
        return new LoginResult(USER_ID, UserRole.CUSTOMER, refreshToken);
    }

    private static Map<String, Object> validRegisterBody() {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("email", "alice@example.com");
        body.put("password", "password123");
        body.put("fullName", "Alice Nguyen");
        body.put("phone", "+84123456789");
        return body;
    }

    private static Map<String, Object> validLoginBody() {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("email", "alice@example.com");
        body.put("password", "password123");
        return body;
    }

    private static Stream<Map<String, Object>> invalidRegisterBodies() {
        Map<String, Object> missingEmail = validRegisterBody();
        missingEmail.remove("email");

        Map<String, Object> blankEmail = validRegisterBody();
        blankEmail.put("email", " ");

        Map<String, Object> missingPassword = validRegisterBody();
        missingPassword.remove("password");

        Map<String, Object> blankPassword = validRegisterBody();
        blankPassword.put("password", " ");

        Map<String, Object> shortPassword = validRegisterBody();
        shortPassword.put("password", "short");

        return Stream.of(missingEmail, blankEmail, missingPassword, blankPassword, shortPassword);
    }

    @Test
    @DisplayName("POST /register ignores a client role and always returns the CUSTOMER result")
    void register_withRoleField_returnsCustomerAndCannotSetRole() throws Exception {
        when(registerUserUseCase.execute(any())).thenReturn(customerResult());
        Map<String, Object> body = validRegisterBody();
        body.put("role", "ADMIN");

        mockMvc.perform(post("/api/v1/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(body)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").value(USER_ID.toString()))
                .andExpect(jsonPath("$.role").value("CUSTOMER"));

        ArgumentCaptor<RegisterUserCommand> captor = ArgumentCaptor.forClass(RegisterUserCommand.class);
        verify(registerUserUseCase).execute(captor.capture());
        assertThat(captor.getValue().email()).isEqualTo("alice@example.com");
        assertThat(captor.getValue().password()).isEqualTo("password123");
        assertThat(captor.getValue().fullName()).isEqualTo("Alice Nguyen");
    }

    @ParameterizedTest(name = "invalid register body #{index} returns 400")
    @MethodSource("invalidRegisterBodies")
    void register_withInvalidBody_returns400(Map<String, Object> body) throws Exception {
        mockMvc.perform(post("/api/v1/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(body)))
                .andExpect(status().isBadRequest());

        verifyNoInteractions(registerUserUseCase);
    }

    @Test
    @DisplayName("POST /register maps duplicate email to 409")
    void register_withDuplicateEmail_returns409() throws Exception {
        when(registerUserUseCase.execute(any()))
                .thenThrow(new DuplicateEmailException("A user with email alice@example.com already exists"));

        mockMvc.perform(post("/api/v1/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(validRegisterBody())))
                .andExpect(status().isConflict());
    }

    @Test
    @DisplayName("POST /login returns both access and refresh tokens")
    void login_withValidCredentials_returnsTokenResponse() throws Exception {
        LoginResult result = loginResult("refresh-token");
        when(loginUseCase.execute(any())).thenReturn(result);
        when(accessTokenIssuer.issue(result.userId(), result.role())).thenReturn("access-token");

        mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(validLoginBody())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.accessToken").value("access-token"))
                .andExpect(jsonPath("$.refreshToken").value("refresh-token"));

        verify(accessTokenIssuer).issue(result.userId(), result.role());
    }

    @Test
    @DisplayName("POST /login uses one identical 401 response for unknown email and wrong password")
    void login_failures_haveByteIdenticalUnauthorizedResponses() throws Exception {
        when(loginUseCase.execute(any()))
                .thenThrow(new InvalidCredentialsException("Invalid email or password"));

        String unknownEmailResponse = mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(validLoginBody())))
                .andExpect(status().isUnauthorized())
                .andReturn()
                .getResponse()
                .getContentAsString();

        Map<String, Object> wrongPasswordBody = validLoginBody();
        wrongPasswordBody.put("password", "wrong-password");
        String wrongPasswordResponse = mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(wrongPasswordBody)))
                .andExpect(status().isUnauthorized())
                .andReturn()
                .getResponse()
                .getContentAsString();

        assertThat(wrongPasswordResponse).isEqualTo(unknownEmailResponse);
    }

    @Test
    @DisplayName("POST /refresh returns the rotated access and refresh tokens")
    void refresh_withValidToken_returnsRotatedTokens() throws Exception {
        LoginResult result = loginResult("new-refresh-token");
        when(refreshUseCase.execute("old-refresh-token")).thenReturn(result);
        when(accessTokenIssuer.issue(result.userId(), result.role())).thenReturn("new-access-token");

        mockMvc.perform(post("/api/v1/auth/refresh")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new RefreshRequest("old-refresh-token"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.accessToken").value("new-access-token"))
                .andExpect(jsonPath("$.refreshToken").value("new-refresh-token"));

        verify(refreshUseCase).execute("old-refresh-token");
    }

    @Test
    @DisplayName("POST /refresh maps an invalid token to 401")
    void refresh_withInvalidToken_returns401() throws Exception {
        when(refreshUseCase.execute("invalid-token"))
                .thenThrow(new InvalidRefreshTokenException("Invalid or expired refresh token"));

        mockMvc.perform(post("/api/v1/auth/refresh")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new RefreshRequest("invalid-token"))))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("POST /logout returns 204 and passes the refresh token to the use case")
    void logout_isIdempotentSafeAndReturnsNoContent() throws Exception {
        mockMvc.perform(post("/api/v1/auth/logout")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new LogoutRequest("refresh-token"))))
                .andExpect(status().isNoContent())
                .andExpect(content().string(""));

        verify(logoutUseCase).execute("refresh-token");
    }
}
