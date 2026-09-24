# Spec: User authentication & authorization (Spring Security)

**Date:** 2026-09-22
**Status:** Ready

---

## Problem Statement
The `domain` module already models `User`/`UserRole`, but no `application`/`infrastructure`/`presentation` code exists for it — nobody can register, log in, or be identified as a request's caller anywhere in the running system, and every existing endpoint (Event/TicketType) is reachable by anyone. This spec builds the User module end-to-end plus a Spring Security JWT layer so requests carry a verifiable identity and role, laying the foundation for authorization on future (and eventually existing) endpoints.

---

## User Stories

- **[P1]** As a guest, I want to register a CUSTOMER account with email + password so I can use the platform.
  Accepted when: `POST /api/v1/auth/register` creates a `CUSTOMER`-role user; rejects a duplicate email with 409; rejects blank/invalid input with 400. The caller cannot set their own role — any `role` field in the request body is ignored or rejected, never trusted.

- **[P1]** As a registered user, I want to log in with email + password and receive an access token + refresh token.
  Accepted when: `POST /api/v1/auth/login` returns 200 + `{accessToken, refreshToken}` on valid credentials (password checked via `PasswordEncoder.matches` against the stored BCrypt hash); returns 401 on wrong password or unknown email, with no difference in response shape between the two (no email-enumeration signal).

- **[P1]** As an authenticated user, I want my Bearer access token to identify me on protected requests.
  Accepted when: a request with a valid, unexpired JWT resolves to an authenticated `SecurityContext` principal carrying `userId` + role-based authority (`ROLE_CUSTOMER`/`ROLE_ORGANIZER`/`ROLE_ADMIN`); a missing, malformed, or expired token on a protected endpoint returns 401.

- **[P1]** As a client app, I want to refresh my access token without re-entering credentials.
  Accepted when: `POST /api/v1/auth/refresh` with a valid, non-revoked, non-expired refresh token returns a new access token and a new refresh token, and immediately revokes the presented refresh token (rotation). Presenting an already-rotated-out, revoked, or expired refresh token returns 401.

- **[P1]** As a user, I want to log out so my refresh token stops working.
  Accepted when: `POST /api/v1/auth/logout` revokes the presented refresh token; a subsequent `/auth/refresh` call with that same token returns 401.

- **[P1]** As an ADMIN, I want to change another user's role so I can grant ORGANIZER or ADMIN access.
  Accepted when: `PATCH /api/v1/users/{id}/role` succeeds (200) only when the caller's JWT role is `ADMIN`; any other caller gets 403; a non-existent `{id}` returns 404. Implemented via the existing domain `User.changeRole()` — no new domain logic.

- **[P2]** As a user, I want to reset a forgotten password via email.
  _(out of scope — needs SMTP/email-sending infra that doesn't exist yet)_

- **[P2]** As the platform, I want to lock an account after repeated failed logins.
  _(out of scope — needs an attempt counter/lockout state not modeled yet)_

- **[P2]** As a new user, I want to verify my email before full access.
  _(out of scope — needs email-sending infra)_

- **[P3]** _(out of scope)_ Retrofitting `@PreAuthorize` role checks onto the existing Event/TicketType controllers (currently `permitAll`) — deferred to a separate follow-up plan, since it requires deciding Event ownership (`organizerId`?), a domain change outside this spec's boundary.

- **[P3]** _(out of scope)_ OAuth2 / social login providers.

---

## Functional Requirements

1. FR-01: `POST /api/v1/auth/register` — body `{email, password, fullName, phone?}` → creates a `User` via the domain's `User.register(...)` with `role = CUSTOMER` always, regardless of request body. 201 + created user id. 409 on duplicate email (`UserRepository.existsByEmail`). 400 on blank/invalid fields.
2. FR-02: `POST /api/v1/auth/login` — body `{email, password}` → 200 + `{accessToken, refreshToken}` on success; 401 on failure (bad password or unknown email, same response either way).
3. FR-03: `POST /api/v1/auth/refresh` — body `{refreshToken}` → validates against the DB-stored, hashed, non-revoked, non-expired token; issues a new access token, rotates the refresh token (old row revoked, new row inserted); 401 otherwise.
4. FR-04: `POST /api/v1/auth/logout` — body `{refreshToken}` → marks the matching stored token revoked; 204. Idempotent-safe: revoking an already-revoked/unknown token also returns 204 (logout never leaks whether a token existed).
5. FR-05: A Spring Security filter chain validates the `Authorization: Bearer <jwt>` header on every request to a non-public path, populating `SecurityContext` with `userId` + a `ROLE_*` authority derived from the JWT's `role` claim. Public paths (register, login, refresh, springdoc/swagger, the existing Event/TicketType GET/POST endpoints per FR-10) require no token.
6. FR-06: `PATCH /api/v1/users/{id}/role` — body `{role}` → guarded by `@PreAuthorize("hasRole('ADMIN')")`; calls `User.changeRole(newRole)`; 200 + updated user; 403 for non-ADMIN callers; 404 if `{id}` doesn't exist.
7. FR-07: Passwords are hashed with Spring Security's `BCryptPasswordEncoder` before being persisted, and verified the same way at login — the raw password is never stored or logged.
8. FR-08: JWTs are issued and verified via Spring Security's built-in Nimbus support (`JwtEncoder`/`JwtDecoder`, HMAC-SHA256, secret from an env-sourced config property, never hardcoded). Claims: `sub` = userId, `role`, `iat`, `exp`.
9. FR-09: Refresh tokens are persisted in a new **infrastructure-only** JPA entity/table (no domain repository port — this is auth plumbing, not a domain aggregate): token id, `userId`, `tokenHash` (SHA-256 of the raw token — the raw value is never stored), `expiresAt`, `revokedAt` (nullable). The raw refresh token is returned to the client exactly once, at issuance/rotation time, and never persisted or logged anywhere afterward.
10. FR-10: The existing Event/TicketType controllers are **not modified** by this plan — they remain reachable without a token, exactly as today.
11. FR-11: `GET /api/v1/users/me` — returns the authenticated caller's own profile (`{id, email, fullName, phone, role}`) from the `SecurityContext` principal. Exists solely as the minimal proof that Bearer-token authentication resolves to a real principal (FR-05); 401 without a valid token.

---

## Non-Functional Requirements

- Performance: `/auth/login` and `/auth/refresh` p95 < 300ms (BCrypt verify + one Postgres round-trip; no external network calls).
- Security: passwords hashed with BCrypt (default Spring Security cost factor); refresh tokens stored as a SHA-256 hash, never raw; JWT signing secret is an environment variable, never committed to the repo (same convention as the existing `.env`-sourced DB credentials); login/refresh/logout responses never reveal whether a given email/token exists when authentication fails.
- Availability: access-token validation is fully stateless (no session store lookup), so the presentation layer remains horizontally scalable without sticky sessions — only refresh/logout touch the DB.

---

## Success Criteria

- [ ] All 6 P1 stories have passing integration tests (MockMvc for controller-level, Testcontainers-backed for anything touching Postgres) added under `presentation`/`bootstrap`.
- [ ] Full reactor `mvnw test` passes with the new module's tests included (no regression in the existing 310).
- [ ] Manual verification against the Docker Compose stack: register → login → call `GET /api/v1/users/me` with the returned Bearer token → 200; same call with no token → 401.
- [ ] Refresh rotation is verified end-to-end: calling `/auth/refresh` twice with the same (first) refresh token succeeds once and returns 401 the second time.
- [ ] `grep -r` across the refresh-token JPA entity/table confirms no field or log statement ever holds the raw token value — only `tokenHash`.

---

## Out of Scope

- Password reset / forgot-password flow (P2 — needs email-sending infra).
- Login rate-limiting / account lockout after repeated failures (P2 — needs attempt-counter state).
- Email verification on registration (P2 — needs email-sending infra).
- Retrofitting `@PreAuthorize` onto the existing Event/TicketType controllers (P3 — separate follow-up plan; requires an Event-ownership domain decision).
- OAuth2 / social login providers (P3).
- Multi-device concurrent refresh-token chains — this plan's rotation model is single-chain per refresh call (see Assumptions).

---

## Assumptions

- Refresh token rotation is single-chain: each `/auth/refresh` call revokes the presented token and issues exactly one new one. This is not a multi-device "3 concurrent active refresh tokens" model — acceptable for P1; revisit if the product later needs concurrent multi-device sessions with independent revocation.
- `GET /api/v1/users/me` (FR-11) is the only "protected endpoint" this plan adds beyond the auth endpoints themselves — it exists purely to prove FR-05 end-to-end, not as a product feature in its own right.
- `spring-boot-starter-oauth2-resource-server` and `spring-security-oauth2-jose` resolve cleanly against the reactor's pinned Spring Boot 4.1.1 / Spring Framework 7 versions; if a version conflict surfaces during planning/implementation, that becomes a plan-level blocker to resolve before Phase 1, not a silent workaround.
- The new User-facing REST layer follows the same package/module conventions already established for Event/TicketType (DTOs + MapStruct mapper in `presentation`, use cases in `application`, JPA entity/repository in `infrastructure`, `GlobalExceptionHandler` extended for any new exception types this introduces).
