package com.tienphat.domain.model;

import com.tienphat.domain.exception.InvalidUserDataException;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.EqualsAndHashCode;
import lombok.Getter;

import java.time.Instant;
import java.util.UUID;

/**
 * Account holder — customer, organizer, or admin.
 *
 * <p>Establishes the entity pattern the rest of the domain repeats: a private builder reachable
 * only through a named static factory, no setters, and identity-based equality. The explicit
 * private all-args constructor is what {@code @Builder} feeds; without it Lombok would generate a
 * package-private one, letting a sibling class in {@code model/} construct a {@code User} that
 * skipped {@link #register} validation.
 */
@Getter
@Builder(access = AccessLevel.PRIVATE)
@EqualsAndHashCode(of = "id")
public class User {

    private final UUID id;
    private String email;
    private String phone;
    private String passwordHash;
    private String fullName;
    private UserRole role;
    private final Instant createdAt;
    private Instant updatedAt;

    private User(UUID id, String email, String phone, String passwordHash, String fullName,
                 UserRole role, Instant createdAt, Instant updatedAt) {
        this.id = id;
        this.email = email;
        this.phone = phone;
        this.passwordHash = passwordHash;
        this.fullName = fullName;
        this.role = role;
        this.createdAt = createdAt;
        this.updatedAt = updatedAt;
    }

    public static User register(UUID id, String email, String phone, String passwordHash,
                                String fullName, UserRole role) {
        if (id == null) {
            throw new InvalidUserDataException("User id must not be null");
        }
        requireNotBlank(email, "email");
        requireNotBlank(passwordHash, "passwordHash");
        requireNotBlank(fullName, "fullName");
        if (role == null) {
            throw new InvalidUserDataException("User role must not be null");
        }

        Instant now = Instant.now();
        return User.builder()
                .id(id)
                .email(email)
                .phone(phone)
                .passwordHash(passwordHash)
                .fullName(fullName)
                .role(role)
                .createdAt(now)
                .updatedAt(now)
                .build();
    }

    /**
     * Rebuilds a user that already exists in storage. For persistence mappers only — application
     * code creating a new account calls {@link #register}.
     *
     * <p>Null checks only, no business validation. The row was validated when it was written, and
     * re-checking it here would mean a later rule change locks the system out of its own history.
     * A null in a required column means the row is corrupt, which is a different problem.
     */
    public static User reconstitute(UUID id, String email, String phone, String passwordHash,
                                    String fullName, UserRole role, Instant createdAt,
                                    Instant updatedAt) {
        requireNotNull(id, "id");
        requireNotNull(email, "email");
        requireNotNull(passwordHash, "passwordHash");
        requireNotNull(fullName, "fullName");
        requireNotNull(role, "role");
        requireNotNull(createdAt, "createdAt");
        requireNotNull(updatedAt, "updatedAt");

        return User.builder()
                .id(id)
                .email(email)
                .phone(phone)
                .passwordHash(passwordHash)
                .fullName(fullName)
                .role(role)
                .createdAt(createdAt)
                .updatedAt(updatedAt)
                .build();
    }

    /**
     * Assigning the role the user already has is not an error — it still bumps {@code updatedAt},
     * so a no-op write is indistinguishable from a real one to callers.
     */
    public void changeRole(UserRole newRole) {
        if (newRole == null) {
            throw new InvalidUserDataException("User role must not be null");
        }
        this.role = newRole;
        touch();
    }

    /** {@code phone} is optional and may be null or blank; {@code fullName} is not. */
    public void updateProfile(String phone, String fullName) {
        requireNotBlank(fullName, "fullName");
        this.phone = phone;
        this.fullName = fullName;
        touch();
    }

    private void touch() {
        this.updatedAt = Instant.now();
    }

    private static void requireNotNull(Object value, String fieldName) {
        if (value == null) {
            throw new InvalidUserDataException("User " + fieldName + " must not be null");
        }
    }

    private static void requireNotBlank(String value, String fieldName) {
        if (value == null || value.isBlank()) {
            throw new InvalidUserDataException("User " + fieldName + " must not be blank");
        }
    }
}
