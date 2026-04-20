package org.example.dumanagementbackend.config;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class OpenApiConfig {

    @Bean
    public OpenAPI duManagementOpenApi() {
        return new OpenAPI().info(new Info()
                .title("DU Management Backend API")
                .version("v1")
                .description("Layered Spring Boot backend generated from ERD and detailed design")
        );
    }
}
