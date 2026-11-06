package dev.elasticsearchmicroservice.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import lombok.ToString;

@Data
@NoArgsConstructor
@EqualsAndHashCode(callSuper = true)
@ToString(callSuper = true)
public class InsertGroupLogRequest extends InsertLogRequest {
    @Schema(
            description = "Group whose sources are fetched",
            example = "prod",
            requiredMode = Schema.RequiredMode.REQUIRED
    )
    private String group;
}
