package dev.elasticsearchmicroservice.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;

import java.time.LocalDate;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class InsertLogRequest {
    @Schema(
            description = "First file date to import (inclusive)",
            example = "2026-10-28",
            requiredMode = Schema.RequiredMode.REQUIRED
    )
    @DateTimeFormat(pattern = "yyyy-MM-dd")
    private LocalDate beginDate;
    @Schema(
            description = "Last file date to import (inclusive)",
            example = "2026-10-29",
            requiredMode = Schema.RequiredMode.REQUIRED
    )
    @DateTimeFormat(pattern = "yyyy-MM-dd")
    private LocalDate endDate;
}
