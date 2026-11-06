package dev.elasticsearchmicroservice.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class InsertLogResponse {
    @Schema(description = "Non-blank lines read; equals inserted + duplicates + rejected", example = "19")
    private Integer processed;
    @Schema(description = "Logs stored in Elasticsearch", example = "13")
    private Integer inserted;
    @Schema(description = "Lines already seen in this run or already stored", example = "2")
    private Integer duplicates;
    @Schema(description = "Invalid, partial or Elasticsearch-rejected lines", example = "4")
    private Integer rejected;
}
