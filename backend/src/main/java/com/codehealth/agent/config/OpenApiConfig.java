package com.codehealth.agent.config;

import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.security.SecurityRequirement;
import io.swagger.v3.oas.models.security.SecurityScheme;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
/**
 *  Author: Kumar.Kunal
 *  Project: codeguardian-ai
 *  File: OpenApiConfig.java
 *  Date: October 31, 2026
 */
@Configuration
public class OpenApiConfig {

    @Bean
    OpenAPI codeHealthOpenApi() {
        return new OpenAPI()
                .info(new Info()
                        .title("Agentic Code Health Engineer API")
                        .version("v1")
                        .description("Monitor, plan, generate, validate, approve, execute and audit code health fixes."))
                .components(new Components().addSecuritySchemes("bearer-jwt",
                        new SecurityScheme().type(SecurityScheme.Type.HTTP).scheme("bearer").bearerFormat("JWT")))
                .addSecurityItem(new SecurityRequirement().addList("bearer-jwt"));
    }
}
