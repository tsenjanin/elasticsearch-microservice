package dev.elasticsearchmicroservice.config;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import static dev.elasticsearchmicroservice.constant.OpenApiConstants.API_DESCRIPTION;
import static dev.elasticsearchmicroservice.constant.OpenApiConstants.API_TITLE;
import static dev.elasticsearchmicroservice.constant.OpenApiConstants.API_VERSION;

@Configuration
public class OpenApiConfig {
    /**
     * Describes the API shown in Swagger UI ({@code /swagger-ui.html}) and in the OpenAPI spec
     * ({@code /v3/api-docs}).
     *
     * @return OpenAPI definition with title, description and version
     */
    @Bean
    public OpenAPI openApi() {
        return new OpenAPI()
                .info(new Info()
                        .title(API_TITLE)
                        .description(API_DESCRIPTION)
                        .version(API_VERSION));
    }
}
