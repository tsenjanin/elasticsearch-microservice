package dev.elasticsearchmicroservice.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class DeleteSourceGroupRequest {
    @Schema(description = "Group to delete", example = "staging", requiredMode = Schema.RequiredMode.REQUIRED)
    private String group;
}
