package dev.elasticsearchmicroservice.service;

import dev.elasticsearchmicroservice.dto.*;
import dev.elasticsearchmicroservice.repository.LogRepository;
import dev.elasticsearchmicroservice.repository.SourceRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.io.UncheckedIOException;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.stream.Collectors;

import static dev.elasticsearchmicroservice.constant.LogConstants.LOG_INDEX_PREFIX;
import static dev.elasticsearchmicroservice.constant.SourceConstants.*;

@Slf4j
@Service
@RequiredArgsConstructor
public class SourceService {
    private static final String GROUP_LABEL = "Group";
    private static final String SOURCE_LABEL = "Source";

    private final SourceRepository sourceRepository;
    private final LogRepository logRepository;

    /**
     * Registers a new log source in a group; the group is created with its first source. Its logs go into
     * {@code request.index}, or {@code application-logs-<group>-<name>} when no index is given. The index itself is
     * created on the first import.
     *
     * @param request group, name, listing URL and optional index of the source
     * @return the stored source
     * @throws ResponseStatusException {@code 400} for an invalid group, name, URL or index; {@code 409} when the
     *                                 group already has this name or the index is used by another source
     * @throws UncheckedIOException    when Elasticsearch cannot be reached
     */
    public SourceDetails insertSource(InsertSourceRequest request) {
        // Validate the input; the index defaults to one derived from group and name
        validateName(GROUP_LABEL, request.getGroup());
        validateName(SOURCE_LABEL, request.getName());
        validateUrl(request.getUrl());
        var index = request.getIndex() == null || request.getIndex().isBlank()
                ? LOG_INDEX_PREFIX + request.getGroup() + INDEX_SEPARATOR + request.getName()
                : request.getIndex();
        validateIndex(index);

        // Two sources must never share an index, as deleting one would wipe the other's logs
        sourceRepository.createSourceIndexIfMissing();
        if (sourceRepository.getSourceForIndex(index).isPresent()) {
            throw new ResponseStatusException(
                    HttpStatus.CONFLICT,
                    "Index '%s' is already used by another source.".formatted(index)
            );
        }

        // Group and name form the document id, so a duplicate name within the group is rejected by Elasticsearch
        var source = new LogSource(request.getGroup(), request.getName(), request.getUrl(), index);
        if (!sourceRepository.insertSource(source)) {
            throw new ResponseStatusException(
                    HttpStatus.CONFLICT,
                    "Source '%s' already exists in group '%s'.".formatted(source.getName(), source.getGroup())
            );
        }
        log.info("Added source '{}' to group '{}' with url '{}' and index '{}'.",
                source.getName(), source.getGroup(), source.getUrl(), index);

        return new SourceDetails(source.getGroup(), source.getName(), source.getUrl(), source.getIndex());
    }

    /**
     * Lists all groups with their sources, both sorted by name.
     *
     * @return one entry per group; empty when no source was added yet
     * @throws UncheckedIOException when Elasticsearch cannot be reached
     */
    public List<GetSourceGroupResponse> getSources() {
        // The repository sorts by group, then name; a LinkedHashMap keeps that order while grouping
        var sourcesByGroup = sourceRepository.getSources()
                .stream()
                .collect(Collectors.groupingBy(
                        LogSource::getGroup,
                        LinkedHashMap::new,
                        Collectors.mapping(this::toSummary, Collectors.toList())));

        return sourcesByGroup.entrySet()
                .stream()
                .map(entry -> new GetSourceGroupResponse(entry.getKey(), entry.getValue()))
                .toList();
    }

    /**
     * Lists the sources of one group, sorted by name.
     *
     * @param group group name
     * @return the group's sources
     * @throws ResponseStatusException {@code 400} for a missing group; {@code 404} when the group has no sources
     * @throws UncheckedIOException    when Elasticsearch cannot be reached
     */
    public List<SourceSummary> getSourcesForGroup(String group) {
        return getLogSourcesForGroup(group)
                .stream()
                .map(this::toSummary)
                .toList();
    }

    /**
     * Lists the log indices of all sources of all groups.
     *
     * @return one index per source; empty when no source was added yet
     * @throws UncheckedIOException when Elasticsearch cannot be reached
     */
    public List<String> getSourceIndices() {
        return sourceRepository.getSources()
                .stream()
                .map(LogSource::getIndex)
                .toList();
    }

    /**
     * Removes one source of a group together with its index and all its logs. The group disappears with its last
     * source.
     *
     * @param request group and name of the source
     * @throws ResponseStatusException {@code 400} for a missing group or name; {@code 404} when the group has no
     *                                 source with this name
     * @throws UncheckedIOException    when Elasticsearch cannot be reached
     */
    public void deleteSource(DeleteSourceRequest request) {
        requirePresent(GROUP_LABEL, request.getGroup());
        requirePresent(SOURCE_LABEL, request.getName());
        var source = sourceRepository.getSource(request.getGroup(), request.getName())
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.NOT_FOUND,
                        "Source '%s' does not exist in group '%s'.".formatted(request.getName(), request.getGroup())
                ));

        deleteSourceWithIndex(source);
    }

    /**
     * Removes a whole group: every source of it together with its index and all its logs. Sources are removed one by
     * one, so after a failure the remaining ones are still listed and a retry finishes the job.
     *
     * @param request group to delete
     * @throws ResponseStatusException {@code 400} for a missing group; {@code 404} when the group has no sources
     * @throws UncheckedIOException    when Elasticsearch cannot be reached
     */
    public void deleteSourceGroup(DeleteSourceGroupRequest request) {
        var sources = getLogSourcesForGroup(request.getGroup());

        sources.forEach(this::deleteSourceWithIndex);
        log.info("Deleted group '{}' with '{}' sources.", request.getGroup(), sources.size());
    }

    /**
     * Loads the stored sources of one group.
     *
     * @param group group name
     * @return the group's sources, never empty
     * @throws ResponseStatusException {@code 400} for a missing group; {@code 404} when the group has no sources
     */
    private List<LogSource> getLogSourcesForGroup(String group) {
        requirePresent(GROUP_LABEL, group);
        var sources = sourceRepository.getSourcesForGroup(group);
        if (sources.isEmpty()) {
            throw new ResponseStatusException(
                    HttpStatus.NOT_FOUND,
                    "Group '%s' does not exist.".formatted(group)
            );
        }

        return sources;
    }

    /**
     * Deletes the index of a source, then its definition. The index goes first, so a failure leaves the source
     * listed and the removal can be retried.
     *
     * @param source stored source
     * @throws UncheckedIOException when Elasticsearch cannot be reached
     */
    private void deleteSourceWithIndex(LogSource source) {
        logRepository.deleteLogIndex(source.getIndex());
        sourceRepository.deleteSource(source.getGroup(), source.getName());
        log.info("Deleted source '{}' of group '{}' and its index '{}'.",
                source.getName(), source.getGroup(), source.getIndex());
    }

    /**
     * Checks that a required parameter is present.
     *
     * @param label parameter name used in the error message
     * @param value parameter value
     * @throws ResponseStatusException {@code 400} when the value is missing or blank
     */
    private void requirePresent(String label, String value) {
        if (value == null || value.isBlank()) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "%s name is required.".formatted(label)
            );
        }
    }

    /**
     * Checks that a group or source name is usable as part of an index name and of the document id.
     *
     * @param label {@code Group} or {@code Source}, used in the error message
     * @param name  name to check
     * @throws ResponseStatusException {@code 400} when the name is missing or malformed
     */
    private void validateName(String label, String name) {
        if (name == null || !NAME_PATTERN.matcher(name).matches()) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "%s name '%s' must match '%s'.".formatted(label, name, NAME_PATTERN.pattern())
            );
        }
    }

    /**
     * Checks that the URL is an absolute http(s) URL with a host. It is otherwise kept exactly as entered.
     *
     * @param url listing URL
     * @throws ResponseStatusException {@code 400} when the URL is missing or not an absolute http(s) URL
     */
    private void validateUrl(String url) {
        var invalid = new ResponseStatusException(
                HttpStatus.BAD_REQUEST,
                "Source url '%s' must be an absolute http or https URL.".formatted(url)
        );
        if (url == null || url.isBlank()) {
            throw invalid;
        }

        URI uri;
        try {
            uri = new URI(url);
        } catch (URISyntaxException e) {
            throw invalid;
        }
        if (uri.getScheme() == null
                || !HTTP_SCHEMES.contains(uri.getScheme().toLowerCase(Locale.ROOT))
                || uri.getHost() == null) {
            throw invalid;
        }
    }

    /**
     * Checks that the index name is valid for Elasticsearch, is not taken over by the built-in {@code logs-*-*}
     * data-stream template and is not one of the indices the service manages itself.
     *
     * @param index index name
     * @throws ResponseStatusException {@code 400} when the index name is not allowed
     */
    private void validateIndex(String index) {
        if (!INDEX_NAME_PATTERN.matcher(index).matches()) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "Index '%s' must match '%s'.".formatted(index, INDEX_NAME_PATTERN.pattern())
            );
        }
        if (DATA_STREAM_INDEX_PATTERN.matcher(index).matches()) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "Index '%s' matches the built-in data stream pattern 'logs-*-*'.".formatted(index)
            );
        }
        if (SOURCE_INDEX.equals(index)) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "Index '%s' is reserved.".formatted(index)
            );
        }
    }

    /**
     * Maps a stored source to its list entry, without the group.
     *
     * @param source stored source
     * @return summary with name, URL and index
     */
    private SourceSummary toSummary(LogSource source) {
        return new SourceSummary(source.getName(), source.getUrl(), source.getIndex());
    }
}
