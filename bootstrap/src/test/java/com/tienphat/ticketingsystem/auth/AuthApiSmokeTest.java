package com.tienphat.ticketingsystem.auth;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.tienphat.domain.model.User;
import com.tienphat.domain.model.UserRole;
import com.tienphat.domain.repository.UserRepository;
import com.tienphat.ticketingsystem.AbstractPostgresIntegrationTest;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.client.NoOpResponseErrorHandler;
import org.springframework.web.client.RestTemplate;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class AuthApiSmokeTest extends AbstractPostgresIntegrationTest {

    private static final String PASSWORD = "correct-password";

    @LocalServerPort
    private int port;

    private final RestTemplate restTemplate = createRestTemplate();

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    private final ObjectMapper objectMapper = new ObjectMapper().findAndRegisterModules();

    @Test
    void fullAuthLifecycle_succeeds() throws Exception {
        String email = uniqueEmail("lifecycle");

        ResponseEntity<String> registration = postJson("/api/v1/auth/register", """
                {
                  "email": "%s",
                  "password": "%s",
                  "fullName": "Lifecycle User",
                  "phone": "0900000001"
                }
                """.formatted(email, PASSWORD));

        assertThat(registration.getStatusCode().value()).isEqualTo(201);
        JsonNode registeredUser = json(registration);
        assertThat(registeredUser.get("email").asText()).isEqualTo(email);
        assertThat(registeredUser.get("role").asText()).isEqualTo("CUSTOMER");

        ResponseEntity<String> login = postJson("/api/v1/auth/login", """
                {
                  "email": "%s",
                  "password": "%s"
                }
                """.formatted(email, PASSWORD));

        assertThat(login.getStatusCode().value()).isEqualTo(200);
        JsonNode initialTokens = json(login);
        String accessToken = initialTokens.get("accessToken").asText();
        String initialRefreshToken = initialTokens.get("refreshToken").asText();
        assertThat(accessToken).isNotBlank();
        assertThat(initialRefreshToken).isNotBlank();

        ResponseEntity<String> me = exchange(
                "/api/v1/users/me", HttpMethod.GET, bearer(accessToken), String.class);
        assertThat(me.getStatusCode().value()).isEqualTo(200);
        JsonNode profile = json(me);
        assertThat(profile.get("id").asText()).isEqualTo(registeredUser.get("id").asText());
        assertThat(profile.get("email").asText()).isEqualTo(email);
        assertThat(profile.get("fullName").asText()).isEqualTo("Lifecycle User");
        assertThat(profile.get("role").asText()).isEqualTo("CUSTOMER");

        ResponseEntity<String> firstRefresh = postJson("/api/v1/auth/refresh", """
                {"refreshToken":"%s"}
                """.formatted(initialRefreshToken));
        assertThat(firstRefresh.getStatusCode().value()).isEqualTo(200);
        JsonNode rotatedTokens = json(firstRefresh);
        String rotatedRefreshToken = rotatedTokens.get("refreshToken").asText();
        assertThat(rotatedRefreshToken).isNotBlank().isNotEqualTo(initialRefreshToken);
        assertThat(rotatedTokens.get("accessToken").asText()).isNotBlank();

        ResponseEntity<String> reusedInitialRefresh = postJson("/api/v1/auth/refresh", """
                {"refreshToken":"%s"}
                """.formatted(initialRefreshToken));
        assertThat(reusedInitialRefresh.getStatusCode().value()).isEqualTo(401);
        assertThat(reusedInitialRefresh.getBody()).contains("Invalid or expired refresh token");

        ResponseEntity<String> logout = postJson("/api/v1/auth/logout", """
                {"refreshToken":"%s"}
                """.formatted(rotatedRefreshToken));
        assertThat(logout.getStatusCode().value()).isEqualTo(204);

        ResponseEntity<String> refreshAfterLogout = postJson("/api/v1/auth/refresh", """
                {"refreshToken":"%s"}
                """.formatted(rotatedRefreshToken));
        assertThat(refreshAfterLogout.getStatusCode().value()).isEqualTo(401);

        ResponseEntity<String> noTokenMe = exchange(
                "/api/v1/users/me", HttpMethod.GET, HttpEntity.EMPTY, String.class);
        assertThat(noTokenMe.getStatusCode().value()).isEqualTo(401);
    }

    @Test
    void registerRejectsClientSuppliedRole() throws Exception {
        String email = uniqueEmail("role-input");

        ResponseEntity<String> registration = postJson("/api/v1/auth/register", """
                {
                  "email": "%s",
                  "password": "%s",
                  "fullName": "Role Input User",
                  "role": "ADMIN"
                }
                """.formatted(email, PASSWORD));

        assertThat(registration.getStatusCode().value()).isEqualTo(201);
        assertThat(json(registration).get("role").asText()).isEqualTo("CUSTOMER");

        User persisted = userRepository.findByEmail(email).orElseThrow();
        assertThat(persisted.getRole()).isEqualTo(UserRole.CUSTOMER);
    }

    @Test
    void roleChange_requiresAdmin() throws Exception {
        String adminEmail = uniqueEmail("admin");
        String targetEmail = uniqueEmail("target");
        String targetPassword = "target-password";

        JsonNode adminRegistration = json(postJson("/api/v1/auth/register", registrationBody(
                adminEmail, PASSWORD, "Admin Candidate")));
        JsonNode targetRegistration = json(postJson("/api/v1/auth/register", registrationBody(
                targetEmail, targetPassword, "Target User")));

        UUID adminId = UUID.fromString(adminRegistration.get("id").asText());
        UUID targetId = UUID.fromString(targetRegistration.get("id").asText());
        User admin = userRepository.findById(adminId).orElseThrow();
        admin.changeRole(UserRole.ADMIN);
        userRepository.save(admin);

        String adminAccessToken = login(adminEmail, PASSWORD).get("accessToken").asText();
        String targetAccessToken = login(targetEmail, targetPassword).get("accessToken").asText();

        ResponseEntity<String> promoted = exchange(
                "/api/v1/users/%s/role".formatted(targetId),
                HttpMethod.PATCH,
                bearerJson(adminAccessToken, "{\"role\":\"ORGANIZER\"}"),
                String.class);
        assertThat(promoted.getStatusCode().value()).isEqualTo(200);
        assertThat(json(promoted).get("role").asText()).isEqualTo("ORGANIZER");
        assertThat(userRepository.findById(targetId).orElseThrow().getRole())
                .isEqualTo(UserRole.ORGANIZER);

        ResponseEntity<String> rejected = exchange(
                "/api/v1/users/%s/role".formatted(targetId),
                HttpMethod.PATCH,
                bearerJson(targetAccessToken, "{\"role\":\"ADMIN\"}"),
                String.class);
        assertThat(rejected.getStatusCode().value()).isEqualTo(403);
    }

    @Test
    void concurrentRefresh_onlyOneWins() throws Exception {
        String email = uniqueEmail("refresh-race");
        register(email, PASSWORD, "Refresh Race User");
        String refreshToken = login(email, PASSWORD).get("refreshToken").asText();

        List<ResponseEntity<String>> responses = runConcurrently(() -> postJson(
                "/api/v1/auth/refresh", "{\"refreshToken\":\"%s\"}".formatted(refreshToken)));

        assertThat(responses).hasSize(2);
        assertThat(responses.stream().filter(response -> response.getStatusCode().value() == 200))
                .hasSize(1);
        List<ResponseEntity<String>> rejected = responses.stream()
                .filter(response -> response.getStatusCode().value() == 401)
                .toList();
        assertThat(rejected).hasSize(1);
        assertThat(rejected.getFirst().getBody()).contains("Invalid or expired refresh token");
    }

    @Test
    void concurrentRegister_onlyOneWins() throws Exception {
        String email = uniqueEmail("register-race");
        String body = registrationBody(email, PASSWORD, "Register Race User");

        List<ResponseEntity<String>> responses = runConcurrently(
                () -> postJson("/api/v1/auth/register", body));

        assertThat(responses).hasSize(2);
        assertThat(responses.stream().filter(response -> response.getStatusCode().value() == 201))
                .hasSize(1);
        assertThat(responses.stream().filter(response -> response.getStatusCode().value() == 409))
                .hasSize(1);
        assertThat(jdbcTemplate.queryForObject(
                "select count(*) from users where email = ?", Integer.class, email))
                .isEqualTo(1);
    }

    private void register(String email, String password, String fullName) throws Exception {
        ResponseEntity<String> response = postJson(
                "/api/v1/auth/register", registrationBody(email, password, fullName));
        assertThat(response.getStatusCode().value()).isEqualTo(201);
    }

    private JsonNode login(String email, String password) throws Exception {
        ResponseEntity<String> response = postJson("/api/v1/auth/login", """
                {
                  "email": "%s",
                  "password": "%s"
                }
                """.formatted(email, password));
        assertThat(response.getStatusCode().value()).isEqualTo(200);
        return json(response);
    }

    private ResponseEntity<String> postJson(String path, String body) {
        return exchange(path, HttpMethod.POST, jsonEntity(body), String.class);
    }

    private <T> ResponseEntity<T> exchange(
            String path, HttpMethod method, HttpEntity<?> request, Class<T> responseType) {
        return restTemplate.exchange(
                "http://localhost:%d%s".formatted(port, path), method, request, responseType);
    }

    private static RestTemplate createRestTemplate() {
        RestTemplate template = new RestTemplate(new JdkClientHttpRequestFactory());
        template.setErrorHandler(new NoOpResponseErrorHandler());
        return template;
    }

    private HttpEntity<String> jsonEntity(String body) {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        return new HttpEntity<>(body, headers);
    }

    private HttpEntity<Void> bearer(String accessToken) {
        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(accessToken);
        return new HttpEntity<>(headers);
    }

    private HttpEntity<String> bearerJson(String accessToken, String body) {
        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(accessToken);
        headers.setContentType(MediaType.APPLICATION_JSON);
        return new HttpEntity<>(body, headers);
    }

    private JsonNode json(ResponseEntity<String> response) throws Exception {
        return objectMapper.readTree(response.getBody());
    }

    private String registrationBody(String email, String password, String fullName) {
        return """
                {
                  "email": "%s",
                  "password": "%s",
                  "fullName": "%s"
                }
                """.formatted(email, password, fullName);
    }

    private String uniqueEmail(String prefix) {
        return "%s-%s@example.com".formatted(prefix, UUID.randomUUID());
    }

    private <T> List<T> runConcurrently(Callable<T> operation) throws Exception {
        ExecutorService executor = Executors.newFixedThreadPool(2);
        CountDownLatch ready = new CountDownLatch(2);
        CountDownLatch start = new CountDownLatch(1);
        try {
            Callable<T> synchronizedOperation = () -> {
                ready.countDown();
                assertThat(start.await(10, TimeUnit.SECONDS)).isTrue();
                return operation.call();
            };

            List<Future<T>> futures = new ArrayList<>();
            futures.add(executor.submit(synchronizedOperation));
            futures.add(executor.submit(synchronizedOperation));
            assertThat(ready.await(10, TimeUnit.SECONDS)).isTrue();
            start.countDown();

            List<T> results = new ArrayList<>();
            for (Future<T> future : futures) {
                results.add(future.get(30, TimeUnit.SECONDS));
            }
            return results;
        } finally {
            executor.shutdownNow();
        }
    }
}
