package dev.elasticsearchmicroservice.service;

import dev.elasticsearchmicroservice.dto.InsertLogRequest;
import dev.elasticsearchmicroservice.dto.InsertLogResponse;
import dev.elasticsearchmicroservice.dto.LogDocument;
import dev.elasticsearchmicroservice.dto.LogFile;
import dev.elasticsearchmicroservice.repository.LogRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;
import tools.jackson.core.JacksonException;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.OffsetDateTime;
import java.time.format.DateTimeParseException;
import java.util.*;

import static dev.elasticsearchmicroservice.constant.LogConstants.*;

@Slf4j
@Service
@RequiredArgsConstructor
public class LogImportService {
    private static final TypeReference<Map<String, Object>> SOURCE_TYPE = new TypeReference<>() {
    };

    private final LogRepository logRepository;
    private final JsonMapper jsonMapper;

    /**
     * Imports ECS-formatted log lines from the given log files into Elasticsearch, regardless of where the files
     * come from.
     * <p>
     * Only files dated within the requested range are read, one at a time, and documents are sent in batches of
     * {@code BULK_BATCH_SIZE}, so memory stays bounded by one file plus one batch even for a full day of logs. Every
     * line gets a deterministic id (SHA-256 of its content); Elasticsearch answers {@code 409} for an id it already
     * holds, so a line repeated within the run or stored in the same index by an earlier run is counted as a
     * duplicate and never inserted twice. Lines that are not valid JSON objects, lack a valid
     * {@code @timestamp} or {@code message}, or are refused by Elasticsearch are rejected.
     *
     * @param request  import range; {@code beginDate} and {@code endDate} are both required and inclusive
     * @param logFiles candidate files; their lines are only read when the file is inside the range
     * @param index    log index of the source the files come from
     * @return counts where {@code processed = inserted + duplicates + rejected}
     * @throws ResponseStatusException {@code 400} when a date is missing or {@code beginDate} is after
     *                                 {@code endDate}
     */
    public InsertLogResponse insertLogs(InsertLogRequest request, List<LogFile> logFiles, String index) {
        // Validate the range and keep the files whose date falls into it, oldest first
        validateDateRange(request);
        var selectedFiles = logFiles.stream()
                .filter(logFile -> !logFile.getDate().isBefore(request.getBeginDate())
                        && !logFile.getDate().isAfter(request.getEndDate()))
                .sorted(Comparator.comparing(LogFile::getDate).thenComparing(LogFile::getIndex))
                .toList();

        // Parse every non-blank line and insert full batches as they fill up
        var batch = new ArrayList<LogDocument>();
        var counts = new InsertLogResponse(0, 0, 0, 0);
        var indexReady = false;
        for (var logFile : selectedFiles) {
            var lines = logFile.getLines().get();
            for (var lineNumber = 1; lineNumber <= lines.size(); lineNumber++) {
                var line = lines.get(lineNumber - 1);
                if (line.isBlank()) {
                    continue;
                }

                counts.setProcessed(counts.getProcessed() + 1);
                var document = parseLog(line);
                if (document.isEmpty()) {
                    counts.setRejected(counts.getRejected() + 1);
                    log.warn("Rejected invalid log at '{}:{}'.", logFile.getName(), lineNumber);
                    continue;
                }

                batch.add(document.get());
                if (batch.size() >= BULK_BATCH_SIZE) {
                    indexReady = insertBatch(batch, counts, indexReady, index);
                }
            }
        }

        // Insert whatever is left in the last, partial batch
        if (!batch.isEmpty()) {
            insertBatch(batch, counts, indexReady, index);
        }
        log.info("Imported logs from '{}' to '{}' into '{}': '{}'.",
                request.getBeginDate(), request.getEndDate(), index, counts);

        return counts;
    }

    /**
     * Checks that both dates are present and that {@code beginDate} is not after {@code endDate}.
     *
     * @param request import request to validate
     * @throws ResponseStatusException {@code 400} when the range is incomplete or reversed
     */
    public void validateDateRange(InsertLogRequest request) {
        if (request.getBeginDate() == null || request.getEndDate() == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Both beginDate and endDate are required.");
        }
        if (request.getBeginDate().isAfter(request.getEndDate())) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "beginDate '%s' is after endDate '%s'.".formatted(request.getBeginDate(), request.getEndDate())
            );
        }
    }

    /**
     * Sends one batch to Elasticsearch, adds its results to the counts and empties the batch.
     * <p>
     * The index is created before the first batch only, so nothing is created when every line is rejected.
     * A {@code 409} item means the id is already stored (an earlier batch or import) and counts as a duplicate.
     *
     * @param batch      documents to insert; cleared afterwards
     * @param counts     running totals, updated in place
     * @param indexReady whether the index was already checked in this run
     * @param index      log index of the source
     * @return {@code true}, as the index exists after this call
     * @throws java.io.UncheckedIOException when Elasticsearch cannot be reached
     */
    private Boolean insertBatch(List<LogDocument> batch, InsertLogResponse counts, Boolean indexReady, String index) {
        // Make sure the index exists before the first insert of the run
        if (!indexReady) {
            logRepository.createLogIndexIfMissing(index);
        }

        // Tally each item: stored, already stored, or refused
        for (var item : logRepository.insertLogs(index, batch)) {
            if (item.error() == null) {
                counts.setInserted(counts.getInserted() + 1);
            } else if (DUPLICATE_STATUS.equals(item.status())) {
                counts.setDuplicates(counts.getDuplicates() + 1);
            } else {
                counts.setRejected(counts.getRejected() + 1);
                log.warn("Rejected log '{}': '{}'.", item.id(), item.error().reason());
            }
        }
        batch.clear();

        return true;
    }

    /**
     * Parses one ECS log line into a document ready for Elasticsearch.
     * <p>
     * A line is valid when it is a JSON object with a string {@code message} and an ISO-8601 {@code @timestamp}
     * that includes an offset. A partially written line (e.g. the last line of the active file) fails to parse.
     *
     * @param line raw log line
     * @return the document with a content-based id, or empty when the line is invalid
     */
    private Optional<LogDocument> parseLog(String line) {
        // Parse JSON; malformed or truncated lines are invalid
        JsonNode node;
        try {
            node = jsonMapper.readTree(line);
        } catch (JacksonException e) {
            return Optional.empty();
        }

        // Require the fields needed for searching and retention
        if (!node.isObject() || !isTimestamp(node.get(TIMESTAMP_FIELD)) || !isText(node.get(MESSAGE_FIELD))) {
            return Optional.empty();
        }

        // The id is a hash of the line, so the same line always maps to the same document
        var source = jsonMapper.convertValue(node, SOURCE_TYPE);

        return Optional.of(new LogDocument(hash(line.strip()), source));
    }

    /**
     * Checks whether a JSON value is a string holding an ISO-8601 timestamp with offset.
     *
     * @param node JSON value, may be {@code null}
     * @return {@code true} when the value parses as {@link OffsetDateTime}
     */
    private Boolean isTimestamp(JsonNode node) {
        if (!isText(node)) {
            return false;
        }

        try {
            OffsetDateTime.parse(node.stringValue());

            return true;
        } catch (DateTimeParseException e) {
            return false;
        }
    }

    /**
     * Checks whether a JSON value is present and is a string.
     *
     * @param node JSON value, may be {@code null}
     * @return {@code true} when the value is a JSON string
     */
    private Boolean isText(JsonNode node) {
        return node != null && node.isString();
    }

    /**
     * Hashes a value with SHA-256.
     *
     * @param value text to hash
     * @return lowercase hex digest
     * @throws IllegalStateException when SHA-256 is unavailable in the JVM
     */
    private String hash(String value) {
        try {
            var digest = MessageDigest.getInstance(HASH_ALGORITHM)
                    .digest(value.getBytes(StandardCharsets.UTF_8));

            return HexFormat.of().formatHex(digest);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("Hash algorithm '%s' is unavailable.".formatted(HASH_ALGORITHM), e);
        }
    }
}
