package dev.elasticsearchmicroservice.util;

import dev.elasticsearchmicroservice.dto.LogFile;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.util.List;
import java.util.Optional;
import java.util.function.Supplier;

import static dev.elasticsearchmicroservice.constant.LogConstants.*;

@Slf4j
@Component
public class LogFileNameParser {
    /**
     * Describes a log file from its name.
     * <p>
     * {@code whatever-yyyy-MM-dd.i.log} is a rolled file whose date and rolling index come from the name; any other
     * {@code whatever.log} is the active file and gets today's date. Rolled names also end in {@code .log}, so they
     * are matched first.
     *
     * @param fileName file name without any archive suffix
     * @param lines    lazy reader of the file's lines, only invoked when the file is imported
     * @return the described file, or empty when the name matches neither format
     */
    public Optional<LogFile> parse(String fileName, Supplier<List<String>> lines) {
        if (fileName == null || fileName.isBlank()) {
            return Optional.empty();
        }

        // Active file: no date in the name, always today, sorted after the rolled files of the same day
        var matcher = ROLLED_LOG_PATTERN.matcher(fileName);
        if (!matcher.matches()) {
            if (fileName.endsWith(ACTIVE_LOG_SUFFIX)) {
                return Optional.of(new LogFile(fileName, LocalDate.now(), ACTIVE_LOG_INDEX, true, lines));
            }

            log.debug("Skipped unrecognized log file '{}'.", fileName);
            return Optional.empty();
        }

        // Rolled file: date and index come from the name

        // The pattern only checks the digit layout, so impossible dates like 2026-13-40 still fail here
        try {
            var date = LocalDate.parse(matcher.group(DATE_GROUP));
            var index = Integer.valueOf(matcher.group(INDEX_GROUP));

            return Optional.of(new LogFile(fileName, date, index, false, lines));
        } catch (DateTimeParseException | NumberFormatException e) {
            log.warn("Skipped log file '{}' with invalid date or index.", fileName);
            return Optional.empty();
        }
    }
}
