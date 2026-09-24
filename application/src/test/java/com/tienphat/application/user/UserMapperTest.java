package com.tienphat.application.user;

import com.tienphat.domain.model.User;
import com.tienphat.domain.model.UserRole;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mapstruct.factory.Mappers;

import java.lang.reflect.RecordComponent;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class UserMapperTest {

    private final UserMapper userMapper = Mappers.getMapper(UserMapper.class);

    @Test
    @DisplayName("toResult() copies every field except passwordHash")
    void toResult_copiesEveryFieldExceptPasswordHash() {
        User user = User.register(UUID.randomUUID(), "user@example.com", "0900000000",
                "hashed-password", "Test User", UserRole.CUSTOMER);

        UserResult result = userMapper.toResult(user);

        assertThat(result.id()).isEqualTo(user.getId());
        assertThat(result.email()).isEqualTo(user.getEmail());
        assertThat(result.fullName()).isEqualTo(user.getFullName());
        assertThat(result.phone()).isEqualTo(user.getPhone());
        assertThat(result.role()).isEqualTo(user.getRole());
        assertThat(result.createdAt()).isEqualTo(user.getCreatedAt());
        assertThat(result.updatedAt()).isEqualTo(user.getUpdatedAt());
    }

    @Test
    @DisplayName("UserResult structurally has no field that could carry passwordHash")
    void userResult_hasNoPasswordHashComponent() {
        RecordComponent[] components = UserResult.class.getRecordComponents();

        assertThat(components)
                .extracting(RecordComponent::getName)
                .doesNotContain("passwordHash", "password");
    }
}
