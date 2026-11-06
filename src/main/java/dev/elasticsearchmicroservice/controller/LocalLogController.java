package dev.elasticsearchmicroservice.controller;

import dev.elasticsearchmicroservice.dto.InsertLogRequest;
import dev.elasticsearchmicroservice.dto.InsertLogResponse;
import dev.elasticsearchmicroservice.service.LocalLogService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springdoc.core.annotations.ParameterObject;
import org.springframework.context.annotation.Profile;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import static dev.elasticsearchmicroservice.constant.LogConstants.LOCAL_PROFILE;

@RestController
@RequestMapping("log")
@RequiredArgsConstructor
@Profile(LOCAL_PROFILE)
@Tag(name = "Log", description = "Import and retention of application logs")
public class LocalLogController {
    private final LocalLogService localLogService;

    /**
     * Imports logs from the resource log files dated between {@code beginDate} and {@code endDate}. Only registered
     * in the {@code local} profile.
     *
     * @param request import range bound from the {@code beginDate} and {@code endDate} query parameters
     * @return {@code 201} with processed, inserted, duplicate and rejected counts
     */
    @PostMapping
    @Operation(
            summary = "Import logs",
            description = """
                    Imports log files from resources whose file date is between beginDate and endDate (inclusive). \
                    The active app.log counts as today. Duplicates are skipped and invalid or partial lines \
                    are rejected. Local development only; disabled in the container."""
    )
    @ApiResponse(responseCode = "201", description = "Logs imported")
    @ApiResponse(responseCode = "400", description = "Missing date or beginDate after endDate")
    public ResponseEntity<InsertLogResponse> insertLogs(@ParameterObject @ModelAttribute InsertLogRequest request) {
        var content = localLogService.insertLogs(request);

        return new ResponseEntity<>(content, HttpStatus.CREATED);
    }
}
