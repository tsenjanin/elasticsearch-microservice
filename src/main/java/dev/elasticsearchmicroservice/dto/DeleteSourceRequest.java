package dev.elasticsearchmicroservice.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class DeleteSourceRequest {
    @Schema(description = "Group of the source", example = "prod", requiredMode = Schema.RequiredMode.REQUIRED)
    private String group;
    @Schema(description = "Source name", example = "public-api", requiredMode = Schema.RequiredMode.REQUIRED)
    private String name;
}
