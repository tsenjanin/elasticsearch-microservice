package dev.elasticsearchmicroservice.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class InsertSourceLogResponse {
    @Schema(description = "Source name", example = "public-api")
    private String source;
    @Schema(description = "Index the logs were imported into", example = "application-logs-public-api")
    private String index;
    @Schema(description = "Non-blank lines read; equals inserted + duplicates + rejected", example = "19")
    private Integer processed;
    @Schema(description = "Logs stored in Elasticsearch", example = "13")
    private Integer inserted;
    @Schema(description = "Lines already seen in this run or already stored", example = "2")
    private Integer duplicates;
    @Schema(description = "Invalid, partial or Elasticsearch-rejected lines", example = "4")
    private Integer rejected;
    @Schema(
            description = "Why the import of this source failed; null on success. Counts are 0 when it failed",
            example = "I/O error on GET request for \"https://logs.example.com/logs/\""
    )
    private String error;
}
