# Phase 1: Runtime dependencies, Docker services, and integration-test foundation

## P1 Stories Satisfied

No product story is completed directly. This phase unblocks all later Redis/RabbitMQ and full-stack tests.

## Affected Areas

- `infrastructure/pom.xml`
- `domain/pom.xml` (annotation-processing build fix required to compile the existing Lombok-based domain)
- `bootstrap/pom.xml` or test dependencies as required by the chosen container test layout
- `docker-compose.yml`
- `bootstrap/src/main/resources/application.yml`
- `infrastructure/src/main/java/com/tienphat/infrastructure/config/`
- `infrastructure/src/test/java/` and `bootstrap/src/test/java/`

## Requirements

1. Add Spring Data Redis and Spring AMQP runtime dependencies to the module that owns the adapters; keep domain/application free of Redis and RabbitMQ classes.
2. Add Redis and RabbitMQ services to Compose with durable volumes/health checks suitable for local development. The application must receive host/port/credential values through environment-backed configuration.
3. Define named properties for Redis, RabbitMQ exchanges/queues, retry delay, and reconciliation interval. Do not hardcode deployment secrets.
4. Add a test foundation that can start PostgreSQL, Redis, and RabbitMQ containers and inject their connection properties into a Spring context. Reuse the existing PostgreSQL singleton pattern where practical.
5. Add minimal topology/configuration smoke tests: Redis responds to `PING`; RabbitMQ can declare the intended durable topology and accept a persistent test message.

## Implementation Steps

1. Add the runtime/test dependencies and verify the Spring Boot 4.1.1 dependency management resolves them without version pins.
2. Add `redis` and `rabbitmq` services to `docker-compose.yml`; add `depends_on` health conditions for the app.
3. Add `spring.data.redis.*` and `spring.rabbitmq.*` settings to the bootstrap configuration and corresponding test overrides.
4. Create infrastructure configuration classes for Redis connection/template/script support and RabbitMQ connection/topology declaration. Keep queues/exchanges named constants, not scattered string literals.
5. Add a test-only multi-container base/configuration and smoke tests. Use `GenericContainer` plus dynamic properties if Boot's `@ServiceConnection` cannot infer the generic Redis/Rabbit images.
6. Run module tests and a Compose startup check before adding feature logic.

## Invariants

- The application must fail to start or fail closed when required Redis configuration is missing; it must not silently use an in-memory stock implementation.
- RabbitMQ order/expiry queues and exchanges are durable and use stable names across restarts.
- Domain and application modules contain no imports from `org.springframework.data.redis`, `org.springframework.amqp`, or client-specific Redis/Rabbit classes.

## Success Criteria

- `./mvnw.cmd -pl infrastructure -am test` passes the Redis/Rabbit topology smoke tests with Docker running.
- `docker compose up -d --build` starts DB, Redis, RabbitMQ, and the app; `docker compose ps` reports healthy dependencies.
- `/v3/api-docs` remains reachable after the new services are added.
- No credentials are committed in Compose or Java source; all runtime values come from environment/configuration.

## Test Strategy

- Unit-test property/topology naming where possible.
- Use real containers for Redis command execution and RabbitMQ declaration/publish checks.
- Preserve the existing PostgreSQL Testcontainers smoke tests and run the full reactor after wiring.

## Rollback Notes

If the phase fails, remove only the new Redis/Rabbit dependencies, Compose services, and configuration entries. Existing PostgreSQL and authentication flows must remain runnable. Do not alter domain models in this phase.

## Risks

- Spring Boot 4.1.1 may require a specific Spring AMQP/Data Redis starter combination; resolve through the parent BOM before pinning versions.
- Generic Testcontainers need explicit dynamic property wiring; a green Maven compile without a real container smoke test is not sufficient.

## Progress

- [x] Add Redis and RabbitMQ runtime/test dependencies.
- [x] Add configurable Compose services, durable volumes, health checks, and app dependency ordering.
- [x] Add environment-backed Spring Redis/RabbitMQ configuration and a messaging feature flag.
- [x] Add durable order-create and TTL/DLX topology configuration.
- [x] Add real PostgreSQL/Redis/RabbitMQ Testcontainers foundation and smoke tests.
- [x] Fix the pre-existing domain Lombok annotation-processor gap so the reactor compiles on the current JDK toolchain.
- [x] Verify Maven tests, Docker Compose startup, service health, and `/v3/api-docs`.

