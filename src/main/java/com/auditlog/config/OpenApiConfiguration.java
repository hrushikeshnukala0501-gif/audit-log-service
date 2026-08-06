package com.auditlog.config;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.info.License;
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
                .description("Append-only, tamper-evident audit event APIs.")
                .license(new License().name("Proprietary")));
    }

    @Bean
    GroupedOpenApi auditLogServiceApi() {
        return GroupedOpenApi.builder()
                .group("audit-log-service")
                .pathsToMatch("/api/v1/audit/**")
                .build();
    }
}
