package dev.elasticsearchmicroservice.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class InsertGroupLogRequest {
    @Schema(
            description = "Group whose sources are fetched",
            example = "prod",
            requiredMode = Schema.RequiredMode.REQUIRED
    )
    private String group;
}
