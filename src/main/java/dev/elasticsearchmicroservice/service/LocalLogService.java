package dev.elasticsearchmicroservice.service;

import dev.elasticsearchmicroservice.dto.InsertLogRequest;
import dev.elasticsearchmicroservice.dto.InsertLogResponse;
import dev.elasticsearchmicroservice.util.LogFileNameParser;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Profile;
import org.springframework.core.io.Resource;
import org.springframework.core.io.support.ResourcePatternResolver;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;

import static dev.elasticsearchmicroservice.constant.LogConstants.LOCAL_LOG_INDEX;
import static dev.elasticsearchmicroservice.constant.LogConstants.LOCAL_PROFILE;
import static dev.elasticsearchmicroservice.constant.LogConstants.LOG_RESOURCE_PATTERN;

@Slf4j
@Service
@RequiredArgsConstructor
@Profile(LOCAL_PROFILE)
public class LocalLogService {
    private final LogImportService logImportService;
    private final LogFileNameParser logFileNameParser;
    private final ResourcePatternResolver resourcePatternResolver;

    /**
     * Imports logs from the log files in {@code src/main/resources/logs}. Only active in the {@code local} profile,
     * which is the default; the container activates another profile, which turns it off.
     * <p>
     * Files are selected by the date in their name ({@code app-yyyy-MM-dd.i.log}); the active {@code app.log} counts
     * as today. Logs go into {@code application-logs-local}. Parsing, deduplication and insertion are done by
     * {@link LogImportService#insertLogs}.
     *
     * @param request import range; {@code beginDate} and {@code endDate} are both required and inclusive
     * @return counts where {@code processed = inserted + duplicates + rejected}
     * @throws org.springframework.web.server.ResponseStatusException {@code 400} for a missing or reversed range
     * @throws UncheckedIOException when log files cannot be read or Elasticsearch cannot be reached
     */
    public InsertLogResponse insertLogs(InsertLogRequest request) {
        // List every file in the logs resource folder
        Resource[] resources;
        try {
            resources = resourcePatternResolver.getResources(LOG_RESOURCE_PATTERN);
        } catch (IOException e) {
            throw new UncheckedIOException("Failed to resolve log files '%s'.".formatted(LOG_RESOURCE_PATTERN), e);
        }

        // Describe recognized log files; their content is only read if they fall into the range
        var logFiles = Arrays.stream(resources)
                .map(resource -> logFileNameParser.parse(resource.getFilename(), () -> readLines(resource)))
                .flatMap(Optional::stream)
                .toList();

        return logImportService.insertLogs(request, logFiles, LOCAL_LOG_INDEX);
    }

    /**
     * Reads a log file as UTF-8 lines. The active file is read as a snapshot of its current content.
     *
     * @param resource log file resource
     * @return all lines, including blank ones
     * @throws UncheckedIOException when the file cannot be read
     */
    private List<String> readLines(Resource resource) {
        try {
            return resource.getContentAsString(StandardCharsets.UTF_8)
                    .lines()
                    .toList();
        } catch (IOException e) {
            throw new UncheckedIOException("Failed to read log file '%s'.".formatted(resource.getFilename()), e);
        }
    }
}
