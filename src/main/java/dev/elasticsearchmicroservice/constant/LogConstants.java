package dev.elasticsearchmicroservice.constant;

import java.time.Duration;
import java.util.regex.Pattern;

public final class LogConstants {
    public static final String LOG_INDEX_PREFIX = "application-logs-";
    public static final String LOG_INDEX_MAPPING_PATH = "elasticsearch/log-index-mapping.json";
    public static final String ACTIVE_LOG_SUFFIX = ".log";
    public static final String DATE_GROUP = "date";
    public static final String INDEX_GROUP = "index";
    public static final Pattern ROLLED_LOG_PATTERN =
            Pattern.compile("^.+-(?<date>\\d{4}-\\d{2}-\\d{2})\\.(?<index>\\d+)\\.log$");
    public static final Integer ACTIVE_LOG_INDEX = Integer.MAX_VALUE;
    public static final Integer BULK_BATCH_SIZE = 1000;
    public static final String LOG_RETENTION = "now-5d";
    public static final String TIMESTAMP_FIELD = "@timestamp";
    public static final String MESSAGE_FIELD = "message";
    public static final Integer DUPLICATE_STATUS = 409;
    public static final String HASH_ALGORITHM = "SHA-256";
    public static final Pattern HREF_PATTERN =
            Pattern.compile("href\\s*=\\s*\"([^\"]+)\"", Pattern.CASE_INSENSITIVE);
    public static final String GZIP_SUFFIX = ".gz";
    public static final Integer MAX_LOG_FILE_BYTES = 100 * 1024 * 1024;
    public static final String PATH_SEPARATOR = "/";
    public static final Duration REMOTE_REQUEST_DELAY = Duration.ofMillis(500);

    private LogConstants() {
    }
}
