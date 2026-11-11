package dev.elasticsearchmicroservice.service;

import dev.elasticsearchmicroservice.client.RemoteLogClient;
import dev.elasticsearchmicroservice.dto.InsertGroupLogRequest;
import dev.elasticsearchmicroservice.dto.InsertSourceLogResponse;
import dev.elasticsearchmicroservice.dto.LogFile;
import dev.elasticsearchmicroservice.dto.SourceSummary;
import dev.elasticsearchmicroservice.util.LogFileNameParser;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClientException;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.zip.GZIPInputStream;

import static dev.elasticsearchmicroservice.constant.LogConstants.*;
import static dev.elasticsearchmicroservice.constant.SourceConstants.CURRENT_DIRECTORY;

@Slf4j
@Service
@RequiredArgsConstructor
public class RemoteLogService {
    private final RemoteLogClient remoteLogClient;
    private final LogImportService logImportService;
    private final LogFileNameParser logFileNameParser;
    private final SourceService sourceService;

    /**
     * Imports today's logs from every source of one group, each into that source's own index.
     * <p>
     * Each source serves an HTML directory listing; every {@code href} is resolved against the source's URL. The
     * active {@code whatever.log} is plain text and counts as today; rolled files are
     * {@code whatever-yyyy-MM-dd.i.log.gz}, gzip archives of {@code whatever-yyyy-MM-dd.i.log}. Only files dated
     * today are downloaded, each after a pause of {@code REMOTE_REQUEST_DELAY} so requests to a source are spaced
     * out. Parsing, deduplication and insertion are done by {@link LogImportService#insertLogs}.
     * <p>
     * A source that fails (unreachable, corrupt archive, oversized file) does not stop the others; its entry carries
     * the error and zero counts. Batches stored before the failure stay stored and count as duplicates on a retry.
     *
     * @param request group to fetch
     * @return one entry per source of the group, sorted by source name
     * @throws org.springframework.web.server.ResponseStatusException {@code 400} for a missing group; {@code 404} for
     *                                                                an unknown group
     * @throws UncheckedIOException                                   when the sources cannot be listed
     */
    public List<InsertSourceLogResponse> insertRemoteLogs(InsertGroupLogRequest request) {
        var sources = sourceService.getSourcesForGroup(request.getGroup());
        var today = LocalDate.now();

        return sources.stream()
                .map(source -> insertSourceLogs(today, source))
                .toList();
    }

    /**
     * Imports the logs of one source and reports its counts, or the failure that stopped it.
     *
     * @param date   day to import
     * @param source source to import from
     * @return counts for the source, or zero counts and the error message when the import failed
     */
    private InsertSourceLogResponse insertSourceLogs(LocalDate date, SourceSummary source) {
        try {
            var url = URI.create(source.getUrl());
            var listing = remoteLogClient.getLogListing(url);
            var counts = logImportService.insertLogs(date, getLogFiles(listing, url), source.getIndex());

            return InsertSourceLogResponse.builder()
                    .source(source.getName())
                    .index(source.getIndex())
                    .processed(counts.getProcessed())
                    .inserted(counts.getInserted())
                    .duplicates(counts.getDuplicates())
                    .rejected(counts.getRejected())
                    .build();
        } catch (RestClientException | UncheckedIOException | IllegalStateException | IllegalArgumentException e) {
            log.warn("Failed to import logs from source '{}': '{}'.", source.getName(), e.getMessage(), e);

            return InsertSourceLogResponse.builder()
                    .source(source.getName())
                    .index(source.getIndex())
                    .processed(0)
                    .inserted(0)
                    .duplicates(0)
                    .rejected(0)
                    .error(e.getMessage())
                    .build();
        }
    }

    /**
     * Extracts the log files referenced by the listing's hrefs.
     * <p>
     * Hrefs are resolved against the listing URL, as a browser would. Hrefs outside the listing's directory and
     * names that are not log files (parent links, {@code ?C=N;O=A} sort links) are skipped. Apache listings link
     * each file twice (icon and name), so hrefs are deduplicated.
     *
     * @param listing HTML listing
     * @param url     full listing URL of the source
     * @return recognized log files with lazy downloaders, in listing order
     */
    private List<LogFile> getLogFiles(String listing, URI url) {
        // Collect unique hrefs in page order
        var directory = url.resolve(CURRENT_DIRECTORY);
        var hrefs = new LinkedHashSet<String>();
        var matcher = HREF_PATTERN.matcher(listing);
        while (matcher.find()) {
            hrefs.add(matcher.group(1));
        }

        var logFiles = new ArrayList<LogFile>();
        for (var href : hrefs) {
            // Resolve against the listing URL and never follow links outside its directory
            URI uri;
            try {
                uri = url.resolve(href);
            } catch (IllegalArgumentException e) {
                log.warn("Skipped invalid href '{}'.", href);
                continue;
            }
            if (!uri.toString().startsWith(directory.toString())) {
                log.debug("Skipped href '{}' outside of '{}'.", href, directory);
                continue;
            }

            // The decoded last path segment is the file name; archives are named after the file they contain
            var path = uri.getPath();
            var fileName = path.substring(path.lastIndexOf(PATH_SEPARATOR) + 1);
            var compressed = fileName.endsWith(GZIP_SUFFIX);
            var logFileName = compressed
                    ? fileName.substring(0, fileName.length() - GZIP_SUFFIX.length())
                    : fileName;
            logFileNameParser.parse(logFileName, () -> downloadLines(uri, compressed))
                    .ifPresent(logFiles::add);
        }

        return logFiles;
    }

    /**
     * Downloads a log file and splits it into lines, decompressing it first when needed.
     *
     * @param uri        file location
     * @param compressed whether the file is a gzip archive
     * @return all lines, including blank ones
     */
    private List<String> downloadLines(URI uri, Boolean compressed) {
        pause(uri);
        var content = remoteLogClient.getLogFile(uri);
        var bytes = compressed ? gunzip(uri, content) : content;

        return new String(bytes, StandardCharsets.UTF_8)
                .lines()
                .toList();
    }

    /**
     * Waits {@code REMOTE_REQUEST_DELAY} before a download, so consecutive requests to a source are spaced out.
     *
     * @param uri file about to be downloaded, used in error messages
     * @throws IllegalStateException when the thread is interrupted while waiting; the interrupt flag is restored
     */
    private void pause(URI uri) {
        try {
            Thread.sleep(REMOTE_REQUEST_DELAY);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Interrupted while waiting to download '%s'.".formatted(uri), e);
        }
    }

    /**
     * Decompresses a gzip archive.
     *
     * @param uri     archive location, used in error messages
     * @param archive gzip content
     * @return the decompressed log file
     * @throws IllegalStateException when the decompressed file exceeds {@code MAX_LOG_FILE_BYTES}
     * @throws UncheckedIOException  when the archive is corrupt
     */
    private byte[] gunzip(URI uri, byte[] archive) {
        try (var gzip = new GZIPInputStream(new ByteArrayInputStream(archive))) {
            // Read one byte past the limit to detect oversized files without inflating them fully
            var content = gzip.readNBytes(MAX_LOG_FILE_BYTES + 1);
            if (content.length > MAX_LOG_FILE_BYTES) {
                throw new IllegalStateException(
                        "Log file in '%s' exceeds %d bytes.".formatted(uri, MAX_LOG_FILE_BYTES)
                );
            }

            return content;
        } catch (IOException e) {
            throw new UncheckedIOException("Failed to decompress '%s'.".formatted(uri), e);
        }
    }
}
