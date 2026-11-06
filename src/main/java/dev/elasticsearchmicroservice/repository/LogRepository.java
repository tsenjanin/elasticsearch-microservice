package dev.elasticsearchmicroservice.repository;

import co.elastic.clients.elasticsearch.ElasticsearchClient;
import co.elastic.clients.elasticsearch._types.Conflicts;
import co.elastic.clients.elasticsearch.core.bulk.BulkOperation;
import co.elastic.clients.elasticsearch.core.bulk.BulkResponseItem;
import dev.elasticsearchmicroservice.dto.LogDocument;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Repository;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.util.List;

import static dev.elasticsearchmicroservice.constant.LogConstants.*;

@Slf4j
@Repository
@RequiredArgsConstructor
public class LogRepository {
    private final ElasticsearchClient elasticsearchClient;

    /**
     * Creates a log index with the mappings from {@code elasticsearch/log-index-mapping.json} when it does not exist
     * yet. Explicit mappings make {@code @timestamp} a date and the ECS level/logger/service fields keywords.
     *
     * @param index log index of one source
     * @throws UncheckedIOException when Elasticsearch cannot be reached or the mapping file cannot be read
     */
    public void createLogIndexIfMissing(String index) {
        try {
            // Nothing to do when the index already exists
            if (elasticsearchClient.indices().exists(e -> e.index(index)).value()) {
                return;
            }

            // Create the index from the mapping file in resources
            try (var mapping = new ClassPathResource(LOG_INDEX_MAPPING_PATH).getInputStream()) {
                elasticsearchClient.indices().create(c -> c
                        .index(index)
                        .withJson(mapping));
            }
            log.info("Created index '{}'.", index);
        } catch (IOException e) {
            throw new UncheckedIOException("Failed to create index '%s'.".formatted(index), e);
        }
    }

    /**
     * Inserts logs with a single bulk request using {@code create} operations.
     * <p>
     * Unlike {@code index}, {@code create} never overwrites: a log whose id is already stored comes back as an item
     * with status {@code 409} instead of replacing the existing document.
     *
     * @param index     log index of one source
     * @param documents logs to insert, each with its content-based id
     * @return one result item per document, in the same order
     * @throws UncheckedIOException when Elasticsearch cannot be reached
     */
    public List<BulkResponseItem> insertLogs(String index, List<LogDocument> documents) {
        // One create operation per document so existing ids are reported, not overwritten
        var operations = documents.stream()
                .map(document -> BulkOperation.of(o -> o
                        .create(c -> c
                                .index(index)
                                .id(document.getId())
                                .document(document.getSource()))))
                .toList();

        try {
            return elasticsearchClient.bulk(b -> b.operations(operations)).items();
        } catch (IOException e) {
            throw new UncheckedIOException("Failed to bulk insert logs into '%s'.".formatted(index), e);
        }
    }

    /**
     * Deletes logs whose {@code @timestamp} is older than the retention period ({@code now-5d}, evaluated by
     * Elasticsearch) from the given indices. Missing indices are skipped, version conflicts are skipped instead of
     * aborting, and the indices are refreshed so the deletion is visible immediately.
     *
     * @param indices log indices to clean up
     * @return number of deleted logs; {@code 0} when there are no indices or none of them exists
     * @throws UncheckedIOException when Elasticsearch cannot be reached
     */
    public Long deleteLogs(List<String> indices) {
        // An empty index list would make Elasticsearch target every index in the cluster
        if (indices.isEmpty()) {
            return 0L;
        }

        try {
            return elasticsearchClient.deleteByQuery(d -> d
                            .index(indices)
                            .ignoreUnavailable(true)
                            .allowNoIndices(true)
                            .conflicts(Conflicts.Proceed)
                            .refresh(true)
                            .query(q -> q
                                    .range(r -> r
                                            .date(dr -> dr
                                                    .field(TIMESTAMP_FIELD)
                                                    .lt(LOG_RETENTION)))))
                    .deleted();
        } catch (IOException e) {
            throw new UncheckedIOException("Failed to delete logs from '%s'.".formatted(indices), e);
        }
    }

    /**
     * Deletes a whole log index with all its logs. A missing index is ignored.
     *
     * @param index log index of one source
     * @throws UncheckedIOException when Elasticsearch cannot be reached
     */
    public void deleteLogIndex(String index) {
        try {
            elasticsearchClient.indices().delete(d -> d
                    .index(index)
                    .ignoreUnavailable(true));
            log.info("Deleted index '{}'.", index);
        } catch (IOException e) {
            throw new UncheckedIOException("Failed to delete index '%s'.".formatted(index), e);
        }
    }
}
