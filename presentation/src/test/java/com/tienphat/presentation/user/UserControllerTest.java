package com.tienphat.presentation.user;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.tienphat.application.user.ChangeUserRoleCommand;
import com.tienphat.application.user.UserResult;
import com.tienphat.application.usecase.UseCase;
import com.tienphat.domain.exception.UserNotFoundException;
import com.tienphat.domain.model.UserRole;
import com.tienphat.presentation.config.SecurityConfig;
import com.tienphat.presentation.user.dto.ChangeRoleRequest;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

import java.time.Instant;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(UserController.class)
@Import({SecurityConfig.class, UserDtoMapperImpl.class})
class UserControllerTest {

    private static final UUID USER_ID = UUID.fromString("018f0f9e-0e39-7f31-9e13-ec7c1f160002");
    private static final UUID TARGET_ID = UUID.fromString("018f0f9e-0e39-7f31-9e13-ec7c1f160003");
    private static final Instant NOW = Instant.parse("2026-01-01T00:00:00Z");

    @Autowired
    private MockMvc mockMvc;

    private final ObjectMapper objectMapper = new ObjectMapper().findAndRegisterModules();

    @MockitoBean
    private UseCase<ChangeUserRoleCommand, UserResult> changeUserRoleUseCase;

    @MockitoBean
    private UseCase<UUID, UserResult> getUserUseCase;

    private static UserResult result(UUID id, UserRole role) {
        return new UserResult(id, "alice@example.com", "Alice Nguyen", "+84123456789",
                role, NOW, NOW);
    }

    private static RequestPostProcessor jwtFor(UUID subject, String role) {
        return jwt()
                .jwt(jwt -> jwt.subject(subject.toString()).claim("role", role))
                .authorities(new SimpleGrantedAuthority("ROLE_" + role));
    }

    @Test
    @DisplayName("PATCH /{id}/role succeeds for an ADMIN JWT")
    void changeRole_withAdminToken_returns200() throws Exception {
        when(changeUserRoleUseCase.execute(any())).thenReturn(result(TARGET_ID, UserRole.ORGANIZER));

        mockMvc.perform(patch("/api/v1/users/{id}/role", TARGET_ID)
                        .with(jwtFor(USER_ID, "ADMIN"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new ChangeRoleRequest(UserRole.ORGANIZER))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(TARGET_ID.toString()))
                .andExpect(jsonPath("$.role").value("ORGANIZER"));

        ArgumentCaptor<ChangeUserRoleCommand> captor = ArgumentCaptor.forClass(ChangeUserRoleCommand.class);
        verify(changeUserRoleUseCase).execute(captor.capture());
        assertThat(captor.getValue().id()).isEqualTo(TARGET_ID);
        assertThat(captor.getValue().newRole()).isEqualTo(UserRole.ORGANIZER);
    }

    @Test
    @DisplayName("PATCH /{id}/role returns 403 for a CUSTOMER JWT")
    void changeRole_withCustomerToken_returns403() throws Exception {
        mockMvc.perform(patch("/api/v1/users/{id}/role", TARGET_ID)
                        .with(jwtFor(USER_ID, "CUSTOMER"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new ChangeRoleRequest(UserRole.ORGANIZER))))
                .andExpect(status().isForbidden());

        verifyNoInteractions(changeUserRoleUseCase);
    }

    @Test
    @DisplayName("PATCH /{id}/role returns 403 for an ORGANIZER JWT")
    void changeRole_withOrganizerToken_returns403() throws Exception {
        mockMvc.perform(patch("/api/v1/users/{id}/role", TARGET_ID)
                        .with(jwtFor(USER_ID, "ORGANIZER"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new ChangeRoleRequest(UserRole.ADMIN))))
                .andExpect(status().isForbidden());

        verifyNoInteractions(changeUserRoleUseCase);
    }

    @Test
    @DisplayName("PATCH /{id}/role returns 401 without a token")
    void changeRole_withoutToken_returns401() throws Exception {
        mockMvc.perform(patch("/api/v1/users/{id}/role", TARGET_ID)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new ChangeRoleRequest(UserRole.ORGANIZER))))
                .andExpect(status().isUnauthorized());

        verifyNoInteractions(changeUserRoleUseCase);
    }

    @Test
    @DisplayName("PATCH /{id}/role returns 404 when the target user does not exist")
    void changeRole_forUnknownUser_returns404() throws Exception {
        when(changeUserRoleUseCase.execute(any()))
                .thenThrow(new UserNotFoundException("User " + TARGET_ID + " not found"));

        mockMvc.perform(patch("/api/v1/users/{id}/role", TARGET_ID)
                        .with(jwtFor(USER_ID, "ADMIN"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new ChangeRoleRequest(UserRole.ADMIN))))
                .andExpect(status().isNotFound());
    }

    @ParameterizedTest(name = "invalid role body {0} returns 400")
    @ValueSource(strings = {"{\"role\":null}", "{\"role\":\"\"}"})
    void changeRole_withInvalidRole_returns400(String body) throws Exception {
        mockMvc.perform(patch("/api/v1/users/{id}/role", TARGET_ID)
                        .with(jwtFor(USER_ID, "ADMIN"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isBadRequest());

        verifyNoInteractions(changeUserRoleUseCase);
    }

    @Test
    @DisplayName("GET /me resolves the user id from the JWT subject")
    void getMe_withAuthenticatedToken_returnsCurrentUser() throws Exception {
        when(getUserUseCase.execute(USER_ID)).thenReturn(result(USER_ID, UserRole.CUSTOMER));

        mockMvc.perform(get("/api/v1/users/me").with(jwtFor(USER_ID, "CUSTOMER")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(USER_ID.toString()))
                .andExpect(jsonPath("$.email").value("alice@example.com"))
                .andExpect(jsonPath("$.role").value("CUSTOMER"));

        verify(getUserUseCase).execute(USER_ID);
    }

    @Test
    @DisplayName("GET /me returns 401 without a token")
    void getMe_withoutToken_returns401() throws Exception {
        mockMvc.perform(get("/api/v1/users/me"))
                .andExpect(status().isUnauthorized());

        verifyNoInteractions(getUserUseCase);
    }
}
