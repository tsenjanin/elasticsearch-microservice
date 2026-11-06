package dev.elasticsearchmicroservice.service;

import dev.elasticsearchmicroservice.dto.DeleteLogResponse;
import dev.elasticsearchmicroservice.repository.LogRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.io.UncheckedIOException;
import java.util.stream.Stream;

import static dev.elasticsearchmicroservice.constant.LogConstants.LOCAL_LOG_INDEX;
import static dev.elasticsearchmicroservice.constant.LogConstants.LOG_RETENTION;

@Slf4j
@Service
@RequiredArgsConstructor
public class LogService {
    private final LogRepository logRepository;
    private final SourceService sourceService;

    /**
     * Deletes all logs whose {@code @timestamp} is older than the retention period ({@code now-5d}) from the index of
     * every source of every group and from the local import index.
     *
     * @return number of deleted logs; {@code 0} when no index exists yet
     * @throws UncheckedIOException when Elasticsearch cannot be reached
     */
    public DeleteLogResponse deleteLogs() {
        var indices = Stream.concat(Stream.of(LOCAL_LOG_INDEX), sourceService.getSourceIndices().stream())
                .toList();

        var deleted = logRepository.deleteLogs(indices);
        log.info("Deleted '{}' logs older than '{}' from '{}'.", deleted, LOG_RETENTION, indices);

        return new DeleteLogResponse(deleted);
    }
}
