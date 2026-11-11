package dev.elasticsearchmicroservice.controller;

import dev.elasticsearchmicroservice.dto.DeleteLogResponse;
import dev.elasticsearchmicroservice.dto.InsertGroupLogRequest;
import dev.elasticsearchmicroservice.dto.InsertSourceLogResponse;
import dev.elasticsearchmicroservice.service.LogService;
import dev.elasticsearchmicroservice.service.RemoteLogService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springdoc.core.annotations.ParameterObject;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("log")
@RequiredArgsConstructor
@Tag(name = "Log", description = "Import and retention of application logs")
public class LogController {
    private final LogService logService;
    private final RemoteLogService remoteLogService;

    /**
     * Imports today's logs from every source of one group.
     *
     * @param request group bound from the {@code group} query parameter
     * @return {@code 201} with processed, inserted, duplicate and rejected counts per source
     */
    @PostMapping("fetch")
    @Operation(
            summary = "Import today's remote logs",
            description = """
                    For every source of the group, reads its HTML listing, then downloads and imports today's files \
                    into the source's index: the active .log file (plain text, counts as today) and today's rolled \
                    gzip archives. Downloads from a source are spaced 500 ms apart. Duplicates within a source's \
                    index are skipped and invalid or partial lines are rejected. A failing source does not stop the \
                    others; its entry carries the error."""
    )
    @ApiResponse(responseCode = "201", description = "Logs imported; see each entry's error for failed sources")
    @ApiResponse(responseCode = "400", description = "Missing group")
    @ApiResponse(responseCode = "404", description = "Unknown group")
    public ResponseEntity<List<InsertSourceLogResponse>> insertRemoteLogs(
            @ParameterObject @ModelAttribute InsertGroupLogRequest request) {
        var content = remoteLogService.insertRemoteLogs(request);

        return new ResponseEntity<>(content, HttpStatus.CREATED);
    }

    /**
     * Deletes logs older than 5 days from the index of every source.
     *
     * @return {@code 200} with the number of deleted logs
     */
    @DeleteMapping
    @Operation(
            summary = "Delete old logs",
            description = "Deletes logs whose @timestamp is older than 5 days from the index of every source."
    )
    @ApiResponse(responseCode = "200", description = "Old logs deleted")
    public ResponseEntity<DeleteLogResponse> deleteLogs() {
        var content = logService.deleteLogs();

        return new ResponseEntity<>(content, HttpStatus.OK);
    }
}
