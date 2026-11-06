package dev.elasticsearchmicroservice.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class DeleteLogResponse {
    @Schema(description = "Logs deleted for being older than 5 days", example = "4")
    private Long deleted;
}
