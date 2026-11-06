package dev.elasticsearchmicroservice.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class GetSourceGroupResponse {
    @Schema(description = "Group name", example = "prod")
    private String group;
    @Schema(description = "Sources of the group, sorted by name")
    private List<SourceSummary> sources;
}
