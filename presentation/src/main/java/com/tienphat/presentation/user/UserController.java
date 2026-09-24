package com.tienphat.presentation.user;

import com.tienphat.application.user.ChangeUserRoleCommand;
import com.tienphat.application.user.UserResult;
import com.tienphat.application.usecase.UseCase;
import com.tienphat.presentation.user.dto.ChangeRoleRequest;
import com.tienphat.presentation.user.dto.UserResponse;
import jakarta.validation.Valid;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@RestController
@RequestMapping("/api/v1/users")
public class UserController {

    private final UseCase<ChangeUserRoleCommand, UserResult> changeUserRoleUseCase;
    private final UseCase<UUID, UserResult> getUserUseCase;
    private final UserDtoMapper mapper;

    public UserController(
            UseCase<ChangeUserRoleCommand, UserResult> changeUserRoleUseCase,
            UseCase<UUID, UserResult> getUserUseCase,
            UserDtoMapper mapper) {
        this.changeUserRoleUseCase = changeUserRoleUseCase;
        this.getUserUseCase = getUserUseCase;
        this.mapper = mapper;
    }

    @PatchMapping("/{id}/role")
    @PreAuthorize("hasRole('ADMIN')")
    public UserResponse changeRole(
            @PathVariable UUID id, @Valid @RequestBody ChangeRoleRequest request) {
        UserResult result = changeUserRoleUseCase.execute(mapper.toCommand(id, request));
        return mapper.toResponse(result);
    }

    @GetMapping("/me")
    public UserResponse getMe(@AuthenticationPrincipal Jwt jwt) {
        UserResult result = getUserUseCase.execute(UUID.fromString(jwt.getSubject()));
        return mapper.toResponse(result);
    }
}
