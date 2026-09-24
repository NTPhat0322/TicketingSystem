package com.tienphat.presentation.config;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.security.SecurityScheme;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class OpenApiConfigTest {

    @Test
    void definesBearerJwtSecurityScheme() {
        OpenAPI openApi = new OpenApiConfig().ticketingSystemOpenApi();

        SecurityScheme scheme = openApi.getComponents()
                .getSecuritySchemes()
                .get(OpenApiConfig.BEARER_AUTH_SCHEME);

        assertThat(scheme).isNotNull();
        assertThat(scheme.getType()).isEqualTo(SecurityScheme.Type.HTTP);
        assertThat(scheme.getScheme()).isEqualTo("bearer");
        assertThat(scheme.getBearerFormat()).isEqualTo("JWT");
    }
}
