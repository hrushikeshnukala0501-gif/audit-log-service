package com.auditlog.config;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.security.SecurityRequirement;
import io.swagger.v3.oas.models.security.SecurityScheme;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.info.License;
import io.swagger.v3.oas.models.info.Contact;
import io.swagger.v3.oas.models.servers.Server;
import org.springdoc.core.models.GroupedOpenApi;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class OpenApiConfiguration {

    @Bean
    OpenAPI auditLogOpenApi() {
        return new OpenAPI().info(new Info()
                .title("Audit Log Service API")
                .version("v1")
                .description("Append-only, tamper-evident audit APIs, including retention, redaction, export, and client-account access reporting.")
                .contact(new Contact().name("Audit Log Service Team"))
                .license(new License().name("Proprietary")))
                .addServersItem(new Server().url("http://localhost:8080").description("Local development"))
                .components(new Components().addSecuritySchemes("apiKey", new SecurityScheme()
                        .type(SecurityScheme.Type.APIKEY)
                        .in(SecurityScheme.In.HEADER)
                        .name(ApiKeyAuthenticationFilter.API_KEY_HEADER)))
                .addSecurityItem(new SecurityRequirement().addList("apiKey"));
    }

    @Bean
    GroupedOpenApi auditLogServiceApi() {
        return GroupedOpenApi.builder()
                .group("audit-log-service")
                .pathsToMatch("/api/v1/audit/**", "/api/v1/compliance/**")
                .build();
    }
}
