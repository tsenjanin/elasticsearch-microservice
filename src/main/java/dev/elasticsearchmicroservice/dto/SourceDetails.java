package dev.elasticsearchmicroservice.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class SourceDetails {
    @Schema(description = "Group of the source", example = "prod")
    private String group;
    @Schema(description = "Source name, unique within its group", example = "public-api")
    private String name;
    @Schema(description = "URL of the HTML listing of log files", example = "https://logs.example.com/logs/")
    private String url;
    @Schema(
            description = "Elasticsearch index holding this source's logs",
            example = "application-logs-prod-public-api"
    )
    private String index;
}
