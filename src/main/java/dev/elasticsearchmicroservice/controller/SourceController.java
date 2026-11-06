package dev.elasticsearchmicroservice.controller;

import dev.elasticsearchmicroservice.dto.*;
import dev.elasticsearchmicroservice.service.SourceService;
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
@RequestMapping("source")
@RequiredArgsConstructor
@Tag(name = "Source", description = "Log sources in groups, each source imported into its own index")
public class SourceController {
    private final SourceService sourceService;

    /**
     * Registers a new log source in a group; the group is created with its first source.
     *
     * @param request group, name, listing URL and optional index, bound from the {@code group}, {@code name},
     *                {@code url} and {@code index} query parameters
     * @return {@code 201} with the stored source
     */
    @PostMapping
    @Operation(
            summary = "Add source",
            description = """
                    Registers a log source in a group by its full listing URL; a new group name creates the group. \
                    Its logs are imported into the given index, or application-logs-<group>-<name> when no index \
                    is given; the index is created on the first import. Sources are stored in Elasticsearch and \
                    survive restarts."""
    )
    @ApiResponse(responseCode = "201", description = "Source added")
    @ApiResponse(responseCode = "400", description = "Invalid group, name, URL or index")
    @ApiResponse(responseCode = "409", description = "Name already in the group, or index used by another source")
    public ResponseEntity<SourceDetails> insertSource(@ParameterObject @ModelAttribute InsertSourceRequest request) {
        var content = sourceService.insertSource(request);

        return new ResponseEntity<>(content, HttpStatus.CREATED);
    }

    /**
     * Lists all groups with their sources.
     *
     * @return {@code 200} with one entry per group, groups and sources sorted by name
     */
    @GetMapping
    @Operation(summary = "List sources", description = "Lists all groups with their sources and indices, by name.")
    @ApiResponse(responseCode = "200", description = "Sources listed")
    public ResponseEntity<List<GetSourceGroupResponse>> getSources() {
        var content = sourceService.getSources();

        return new ResponseEntity<>(content, HttpStatus.OK);
    }

    /**
     * Removes one source of a group together with its index and all its logs.
     *
     * @param request group and name, bound from the {@code group} and {@code name} query parameters
     * @return {@code 204}
     */
    @DeleteMapping
    @Operation(
            summary = "Delete source",
            description = """
                    Removes the source from its group and deletes its index with all its logs. The group \
                    disappears with its last source."""
    )
    @ApiResponse(responseCode = "204", description = "Source and its index deleted")
    @ApiResponse(responseCode = "400", description = "Missing group or name")
    @ApiResponse(responseCode = "404", description = "Unknown source in this group")
    public ResponseEntity<Void> deleteSource(@ParameterObject @ModelAttribute DeleteSourceRequest request) {
        sourceService.deleteSource(request);

        return new ResponseEntity<>(HttpStatus.NO_CONTENT);
    }

    /**
     * Removes a whole group: all its sources together with their indices and logs.
     *
     * @param request group, bound from the {@code group} query parameter
     * @return {@code 204}
     */
    @DeleteMapping("group")
    @Operation(
            summary = "Delete group",
            description = "Removes every source of the group and deletes their indices with all their logs."
    )
    @ApiResponse(responseCode = "204", description = "Group, its sources and their indices deleted")
    @ApiResponse(responseCode = "400", description = "Missing group")
    @ApiResponse(responseCode = "404", description = "Unknown group")
    public ResponseEntity<Void> deleteSourceGroup(@ParameterObject @ModelAttribute DeleteSourceGroupRequest request) {
        sourceService.deleteSourceGroup(request);

        return new ResponseEntity<>(HttpStatus.NO_CONTENT);
    }
}
