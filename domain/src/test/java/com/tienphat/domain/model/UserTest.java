package com.tienphat.domain.model;

import com.tienphat.domain.exception.InvalidUserDataException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class UserTest {

    private static final UUID ID = UUID.randomUUID();

    private static User aCustomer() {
        return User.register(ID, "phat@example.com", "0900000000", "hashed", "Tien Phat", UserRole.CUSTOMER);
    }

    @Test
    @DisplayName("register() returns a user carrying the given fields, with createdAt == updatedAt")
    void register_succeeds() {
        User user = aCustomer();

        assertThat(user.getId()).isEqualTo(ID);
        assertThat(user.getEmail()).isEqualTo("phat@example.com");
        assertThat(user.getPhone()).isEqualTo("0900000000");
        assertThat(user.getPasswordHash()).isEqualTo("hashed");
        assertThat(user.getFullName()).isEqualTo("Tien Phat");
        assertThat(user.getRole()).isEqualTo(UserRole.CUSTOMER);
        assertThat(user.getCreatedAt()).isEqualTo(user.getUpdatedAt());
    }

    @Test
    @DisplayName("register() rejects a blank email")
    void register_throwsOnBlankEmail() {
        assertThatThrownBy(() -> User.register(ID, "   ", "0900000000", "hashed", "Tien Phat", UserRole.CUSTOMER))
                .isInstanceOf(InvalidUserDataException.class)
                .hasMessageContaining("email");
    }

    @Test
    @DisplayName("register() rejects a null email")
    void register_throwsOnNullEmail() {
        assertThatThrownBy(() -> User.register(ID, null, "0900000000", "hashed", "Tien Phat", UserRole.CUSTOMER))
                .isInstanceOf(InvalidUserDataException.class)
                .hasMessageContaining("email");
    }

    @Test
    @DisplayName("register() rejects a blank passwordHash")
    void register_throwsOnBlankPasswordHash() {
        assertThatThrownBy(() -> User.register(ID, "phat@example.com", "0900000000", "", "Tien Phat", UserRole.CUSTOMER))
                .isInstanceOf(InvalidUserDataException.class)
                .hasMessageContaining("passwordHash");
    }

    @Test
    @DisplayName("register() rejects a null role")
    void register_throwsOnNullRole() {
        assertThatThrownBy(() -> User.register(ID, "phat@example.com", "0900000000", "hashed", "Tien Phat", null))
                .isInstanceOf(InvalidUserDataException.class)
                .hasMessageContaining("role");
    }

    @Test
    @DisplayName("register() rejects a null id")
    void register_throwsOnNullId() {
        assertThatThrownBy(() -> User.register(null, "phat@example.com", "0900000000", "hashed", "Tien Phat", UserRole.CUSTOMER))
                .isInstanceOf(InvalidUserDataException.class)
                .hasMessageContaining("id");
    }

    @Test
    @DisplayName("register() accepts a null phone — the column is optional")
    void register_acceptsNullPhone() {
        User user = User.register(ID, "phat@example.com", null, "hashed", "Tien Phat", UserRole.CUSTOMER);

        assertThat(user.getPhone()).isNull();
    }

    @Test
    @DisplayName("changeRole() updates the role and does not move updatedAt backwards")
    void changeRole_succeeds() {
        User user = aCustomer();

        user.changeRole(UserRole.ORGANIZER);

        assertThat(user.getRole()).isEqualTo(UserRole.ORGANIZER);
        assertThat(user.getUpdatedAt()).isAfterOrEqualTo(user.getCreatedAt());
    }

    @Test
    @DisplayName("changeRole() to the same role is allowed, not an error")
    void changeRole_toSameRoleIsAllowed() {
        User user = aCustomer();

        user.changeRole(UserRole.CUSTOMER);

        assertThat(user.getRole()).isEqualTo(UserRole.CUSTOMER);
    }

    @Test
    @DisplayName("changeRole() rejects null")
    void changeRole_throwsOnNull() {
        User user = aCustomer();

        assertThatThrownBy(() -> user.changeRole(null))
                .isInstanceOf(InvalidUserDataException.class);
    }

    @Test
    @DisplayName("updateProfile() updates phone and fullName")
    void updateProfile_succeeds() {
        User user = aCustomer();

        user.updateProfile("0911111111", "Nguyen Tien Phat");

        assertThat(user.getPhone()).isEqualTo("0911111111");
        assertThat(user.getFullName()).isEqualTo("Nguyen Tien Phat");
        assertThat(user.getUpdatedAt()).isAfterOrEqualTo(user.getCreatedAt());
    }

    @Test
    @DisplayName("updateProfile() accepts a null phone but rejects a blank fullName")
    void updateProfile_phoneOptionalFullNameRequired() {
        User user = aCustomer();

        user.updateProfile(null, "Nguyen Tien Phat");
        assertThat(user.getPhone()).isNull();

        assertThatThrownBy(() -> user.updateProfile("0911111111", "  "))
                .isInstanceOf(InvalidUserDataException.class)
                .hasMessageContaining("fullName");
    }

    @Test
    @DisplayName("updateProfile() leaves fullName untouched when it throws")
    void updateProfile_doesNotMutateOnValidationFailure() {
        User user = aCustomer();

        assertThatThrownBy(() -> user.updateProfile("0911111111", ""))
                .isInstanceOf(InvalidUserDataException.class);

        assertThat(user.getFullName()).isEqualTo("Tien Phat");
        assertThat(user.getPhone()).isEqualTo("0900000000");
    }

    @Test
    @DisplayName("reconstitute() restores the stored timestamps register() would have overwritten")
    void reconstitute_preservesStoredState() {
        Instant createdAt = Instant.parse("2026-01-10T08:00:00Z");
        Instant updatedAt = Instant.parse("2026-03-02T11:45:00Z");

        User user = User.reconstitute(ID, "phat@example.com", null, "hashed", "Tien Phat",
                UserRole.ADMIN, createdAt, updatedAt);

        assertThat(user.getCreatedAt()).as("register() would force now()").isEqualTo(createdAt);
        assertThat(user.getUpdatedAt()).isEqualTo(updatedAt);
        assertThat(user.getRole()).isEqualTo(UserRole.ADMIN);
        assertThat(user.getPhone()).as("phone stays optional").isNull();
    }

    @Test
    @DisplayName("reconstitute() rejects a null in a required column")
    void reconstitute_rejectsNullRequiredField() {
        assertThatThrownBy(() -> User.reconstitute(ID, "phat@example.com", null, "hashed", "Tien Phat",
                UserRole.ADMIN, null, Instant.now()))
                .isInstanceOf(InvalidUserDataException.class)
                .hasMessageContaining("createdAt");
    }

    @Test
    @DisplayName("equality is by id alone — same id, different fields, still equal")
    void equality_isIdentityBased() {
        User one = User.register(ID, "a@example.com", "0900000000", "hash-a", "Name A", UserRole.CUSTOMER);
        User two = User.register(ID, "b@example.com", "0911111111", "hash-b", "Name B", UserRole.ADMIN);

        assertThat(one).isEqualTo(two);
        assertThat(one).hasSameHashCodeAs(two);
    }

    @Test
    @DisplayName("different ids are not equal, even with identical fields")
    void equality_differentIdsAreNotEqual() {
        User one = User.register(UUID.randomUUID(), "a@example.com", "0900000000", "hash", "Name", UserRole.CUSTOMER);
        User two = User.register(UUID.randomUUID(), "a@example.com", "0900000000", "hash", "Name", UserRole.CUSTOMER);

        assertThat(one).isNotEqualTo(two);
    }
}
