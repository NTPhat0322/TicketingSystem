package com.tienphat.presentation.user;

import com.tienphat.application.user.ChangeUserRoleCommand;
import com.tienphat.application.user.UserResult;
import com.tienphat.domain.model.UserRole;
import com.tienphat.presentation.user.dto.ChangeRoleRequest;
import com.tienphat.presentation.user.dto.UserResponse;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mapstruct.factory.Mappers;

import java.time.Instant;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class UserDtoMapperTest {

    private final UserDtoMapper mapper = Mappers.getMapper(UserDtoMapper.class);

    @Test
    @DisplayName("UserResult maps every profile field to UserResponse")
    void toResponse_preservesEveryField() {
        Instant createdAt = Instant.parse("2026-01-01T00:00:00Z");
        Instant updatedAt = Instant.parse("2026-01-02T00:00:00Z");
        UserResult result = new UserResult(
                UUID.randomUUID(), "alice@example.com", "Alice Nguyen", "+84123456789",
                UserRole.ORGANIZER, createdAt, updatedAt);

        UserResponse response = mapper.toResponse(result);

        assertThat(response.id()).isEqualTo(result.id());
        assertThat(response.email()).isEqualTo(result.email());
        assertThat(response.fullName()).isEqualTo(result.fullName());
        assertThat(response.phone()).isEqualTo(result.phone());
        assertThat(response.role()).isEqualTo(result.role());
        assertThat(response.createdAt()).isEqualTo(result.createdAt());
        assertThat(response.updatedAt()).isEqualTo(result.updatedAt());
    }

    @Test
    @DisplayName("ChangeRoleRequest maps the path id and body role to ChangeUserRoleCommand")
    void toCommand_preservesPathIdAndRequestedRole() {
        UUID id = UUID.randomUUID();
        ChangeRoleRequest request = new ChangeRoleRequest(UserRole.ADMIN);

        ChangeUserRoleCommand command = mapper.toCommand(id, request);

        assertThat(command.id()).isEqualTo(id);
        assertThat(command.newRole()).isEqualTo(UserRole.ADMIN);
    }
}
