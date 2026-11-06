package dev.elasticsearchmicroservice.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class InsertSourceRequest {
    @Schema(
            description = """
                    Group of the source; created with its first source. Lowercase letters, digits and '-', \
                    starting with a letter or digit, at most 50 characters""",
            example = "prod",
            requiredMode = Schema.RequiredMode.REQUIRED
    )
    private String group;
    @Schema(
            description = """
                    Source name, unique within its group. Lowercase letters, digits and '-', starting with a \
                    letter or digit, at most 50 characters""",
            example = "public-api",
            requiredMode = Schema.RequiredMode.REQUIRED
    )
    private String name;
    @Schema(
            description = "Full URL of the HTML listing of log files; hrefs are resolved against it as a browser would",
            example = "https://logs.example.com/logs/",
            requiredMode = Schema.RequiredMode.REQUIRED
    )
    private String url;
    @Schema(
            description = """
                    Elasticsearch index for this source's logs; defaults to application-logs-<group>-<name>. \
                    Lowercase letters, digits, '.', '_' and '-', starting with a letter or digit, at most 200 \
                    characters. Must not match logs-*-* or be used by another source""",
            example = "public-api-logs",
            requiredMode = Schema.RequiredMode.NOT_REQUIRED
    )
    private String index;
}
